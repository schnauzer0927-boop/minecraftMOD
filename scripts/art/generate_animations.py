#!/usr/bin/env python3
"""Rebuild the authored APEX animation library, without third-party dependencies.

Coordinates follow the Bedrock geometry: front is -Z and the left side is +X.
Rotations are additive to the neutral, already bent digitigrade mesh. Locomotion
uses planted heel/toe phases, hip/shoulder counterrotation and lagging blades;
attacks use authored anticipation, contact and overshoot poses. Root translation
is deliberately small: server movement remains authoritative for collisions.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "src/main/resources/assets/apexpredator/animations/apex.animation.json"

BONES = (
    "root", "body", "waist", "chest", "neck", "head", "left_shoulder",
    "right_shoulder", "left_arm", "right_arm", "left_forearm", "right_forearm",
    "left_blade", "right_blade", "left_hand", "right_hand", "left_leg", "right_leg",
    "left_lower_leg", "right_lower_leg", "left_foot", "right_foot", "back_spines",
    "back_blade_left", "back_blade_right", "tail_or_rear_spine", "face_plate", "eye_glow",
)

# The rest shape is tense, slightly asymmetric and predatory. Feet stay aligned
# with the world while the chest carries most of the forward lean.
REST = {
    "body": (3, 0, 0), "waist": (-3, 0, 0), "chest": (10, 0, 0),
    "neck": (-6, 0, 0), "head": (-5, 0, 0),
    "left_shoulder": (0, -3, -5), "right_shoulder": (0, 3, 5),
    "left_arm": (-9, -7, -8), "right_arm": (-6, 7, 8),
    "left_forearm": (-15, 0, -3), "right_forearm": (-18, 0, 3),
    "left_hand": (8, 0, -4), "right_hand": (8, 0, 4),
    "left_leg": (-3, 0, -2), "right_leg": (-3, 0, 2),
    "left_lower_leg": (7, 0, 0), "right_lower_leg": (7, 0, 0),
    "left_foot": (-4, 0, 0), "right_foot": (-4, 0, 0),
    "back_spines": (-5, 0, 0), "back_blade_left": (-4, -6, -5),
    "back_blade_right": (-4, 6, 5), "tail_or_rear_spine": (5, 0, 0),
}

RAGE = {
    **REST, "body": (6, 0, 0), "chest": (19, 0, 0), "neck": (-12, 0, 0),
    "head": (-10, 0, 0), "left_shoulder": (-6, -8, -16),
    "right_shoulder": (-6, 8, 16), "left_arm": (-18, -15, -19),
    "right_arm": (-18, 15, 19), "left_forearm": (-32, 0, -9),
    "right_forearm": (-32, 0, 9), "left_leg": (-12, 0, -7),
    "right_leg": (-12, 0, 7), "left_lower_leg": (19, 0, 0),
    "right_lower_leg": (19, 0, 0), "left_foot": (-7, 0, 0),
    "right_foot": (-7, 0, 0), "back_spines": (-31, 0, 0),
    "back_blade_left": (-17, -28, -32), "back_blade_right": (-17, 28, 32),
    "tail_or_rear_spine": (24, 0, 0), "face_plate": (8, 0, 0),
}


def pose(changes=None, *, position=None, scale=None, rage=False):
    """A complete pose prevents previous attack channels leaking into a new clip."""
    rotations = {name: list((RAGE if rage else REST).get(name, (0, 0, 0))) for name in BONES}
    for bone, vector in (changes or {}).items():
        assert bone in BONES, bone
        rotations[bone] = list(vector)
    positions = {"root": [0, 0, 0], "chest": [0, 0, 0]}
    positions.update({k: list(v) for k, v in (position or {}).items()})
    scales = {"eye_glow": [1, 1, 1]}
    scales.update({k: list(v) for k, v in (scale or {}).items()})
    return {"rotation": rotations, "position": positions, "scale": scales}


def mirror(source):
    """Mirror a authored whole-body pose, including blade lag and local travel."""
    result = {channel: {} for channel in source}
    for channel, tracks in source.items():
        for bone, vector in tracks.items():
            target = bone
            if bone.startswith("left_"):
                target = "right_" + bone[5:]
            elif bone.startswith("right_"):
                target = "left_" + bone[6:]
            elif bone.endswith("_left"):
                target = bone[:-5] + "_right"
            elif bone.endswith("_right"):
                target = bone[:-6] + "_left"
            if channel == "rotation":
                vector = [vector[0], -vector[1], -vector[2]]
            elif channel == "position":
                vector = [-vector[0], vector[1], vector[2]]
            result[channel][target] = list(vector)
    return result


ANIMATIONS = {}


def clip(name, length, frames, loop=False, smooth=True):
    assert frames[0][0] == 0 and frames[-1][0] == length, name
    assert all(a[0] < b[0] for a, b in zip(frames, frames[1:])), name
    bones = {}
    for channel in ("rotation", "position", "scale"):
        names = sorted(set().union(*(frame[channel].keys() for _, frame in frames)))
        for bone in names:
            default = [1, 1, 1] if channel == "scale" else [0, 0, 0]
            values = [(time, frame[channel].get(bone, default)) for time, frame in frames]
            # Static channels use a vector directly; dynamic channels keep their
            # full anticipation/contact/recovery keys for GeckoLib interpolation.
            if all(vector == values[0][1] for _, vector in values):
                track = values[0][1]
            else:
                track = {}
                for time, vector in values:
                    if smooth:
                        # GeckoLib 4.4.9 accepts Bedrock pre/post vectors but
                        # discards their lerp_mode while baking. Its explicit
                        # vector/easing format preserves authored smooth easing.
                        track[f"{time:.4f}".rstrip("0").rstrip(".") or "0"] = {
                            "vector": vector, "easing": "easeInOutSine"
                        }
                    else:
                        track[f"{time:.4f}".rstrip("0").rstrip(".") or "0"] = vector
            bones.setdefault(bone, {})[channel] = track
    ANIMATIONS[f"animation.apex.{name}"] = {
        "loop": loop, "animation_length": length, "bones": bones
    }


def breathing(name, length, rage=False):
    frames = []
    for i in range(9):
        t = i / 8
        breath = math.sin(t * math.tau)
        scan = math.sin(t * math.tau) * (7 if rage else 4)
        base = RAGE if rage else REST
        r = {
            "chest": (base["chest"][0] + breath * (2.4 if rage else 1.3), -scan * .18, 0),
            "neck": (base["neck"][0] - breath * .7, scan * .22, 0),
            "head": (base["head"][0] - breath * .5, scan, -scan * .12),
            "left_shoulder": (base["left_shoulder"][0] - breath, -3, -5 - breath),
            "right_shoulder": (base["right_shoulder"][0] + breath * .7, 3, 5 + breath),
            "left_forearm": (base["left_forearm"][0] + breath * 2, 0, -3),
            "right_forearm": (base["right_forearm"][0] - breath * 1.5, 0, 3),
            "back_spines": (base["back_spines"][0] - breath * (3 if rage else 1), 0, 0),
            "tail_or_rear_spine": (base["tail_or_rear_spine"][0], math.sin(t * math.tau + .6) * 3, 0),
        }
        if rage:
            r.update({"left_shoulder": (-6 - breath * 2, -8, -16 - breath * 2),
                      "right_shoulder": (-6 + breath, 8, 16 + breath * 2),
                      "left_forearm": (-32 + breath * 3, 0, -9),
                      "right_forearm": (-32 - breath * 3, 0, 9)})
        frames.append((round(length * t, 4), pose(r, position={"chest": (0, breath * .12, -breath * .08)}, rage=rage)))
    frames[-1] = (length, frames[0][1])
    clip(name, length, frames, True)


breathing("idle", 3.0)
breathing("rage_idle", 2.2, True)


def locomotion(name, duration, speed, lean, rage=False):
    """Each leg cycles through push-off, folded flight, reach, heel contact.

    Eight authored samples avoid the stiff pendulum gait common in placeholder
    animations. The knee folds only in the air; the toe compensates on contact.
    """
    swing = [-27, -12, 22, 35, 23, 6, -15, -31]
    knee = [8, 5, 16, 38, 48, 35, 17, 9]
    toe = [17, 4, -12, -35, -27, -16, 7, 22]
    frames = []
    for i in range(9):
        j = i % 8
        other = (j + 4) % 8
        phase = j / 8 * math.tau
        bob = math.cos(phase * 2) * (.3 if speed < 1 else .6)
        twist = math.sin(phase) * (7 if speed < 1 else 11)
        roll = math.cos(phase) * (2 if speed < 1 else 3)
        brace = 1.1 if rage else 1
        r = {
            "body": (3 + lean * .16, twist * .22, roll),
            "waist": (-3, -twist, -roll * .7),
            "chest": (lean, twist * 1.25, -roll * .5),
            "neck": (-lean * .5, -twist * .5, 0), "head": (-lean * .42, -twist * .5, 0),
            "left_leg": (swing[j] * speed, 3, -3),
            "right_leg": (swing[other] * speed, -3, 3),
            "left_lower_leg": (knee[j] * speed, 0, 0),
            "right_lower_leg": (knee[other] * speed, 0, 0),
            "left_foot": (toe[j] * speed, 0, -2),
            "right_foot": (toe[other] * speed, 0, 2),
            "left_shoulder": (-4, -twist * .4, -10 * brace),
            "right_shoulder": (-4, -twist * .4, 10 * brace),
            "left_arm": (-swing[j] * speed * .52 - 10, -10, -12 * brace),
            "right_arm": (-swing[other] * speed * .52 - 10, 10, 12 * brace),
            "left_forearm": (-28 - knee[other] * .2, -3, -8),
            "right_forearm": (-28 - knee[j] * .2, 3, 8),
            "left_blade": (math.sin(phase - .5) * 5, 0, -4),
            "right_blade": (-math.sin(phase - .5) * 5, 0, 4),
            "tail_or_rear_spine": (10 + lean * .25, -twist * .65, roll),
        }
        if speed > 1.4:
            # At full sprint the forearms tuck behind the torso, reducing drag.
            r.update({"left_arm": (28 + math.sin(phase) * 10, -17, -14),
                      "right_arm": (28 - math.sin(phase) * 10, 17, 14),
                      "left_forearm": (-58, 0, -9), "right_forearm": (-58, 0, 9)})
        frames.append((round(duration * i / 8, 4), pose(r, position={"root": (0, bob - (1.8 if speed > 1.4 else .2), 0)}, rage=rage)))
    clip(name, duration, frames, True)


locomotion("walk", 1.0, .7, 13)
locomotion("run", .64, 1.1, 23)
locomotion("sprint", .44, 1.6, 39)
locomotion("rage_run", .52, 1.35, 31, True)

# Cross-step strafing keeps the head trained on the target while pelvis and
# shoulders bank in opposite directions. It is distinct from forward running.
strafe_frames = []
for i, (hip, knee, arm, offset) in enumerate([
    (-11, 9, -8, 0), (-18, 18, -16, -.5), (-10, 29, -5, -.9),
    (3, 18, 9, -.3), (12, 9, 15, 0), (21, 18, 23, .5),
    (11, 29, 11, .9), (-3, 18, -2, .3), (-11, 9, -8, 0),
]):
    strafe_frames.append((round(.72 * i / 8, 4), pose({
        "body": (5, 4, -7), "waist": (-3, -10, 9), "chest": (14, 16, -5),
        "neck": (-8, -6, 2), "head": (-5, -10, 3),
        "left_leg": (-8, 0, hip - 7), "right_leg": (-4, 0, -hip + 3),
        "left_lower_leg": (knee, 0, -4), "right_lower_leg": (38 - knee, 0, 4),
        "left_foot": (-knee * .5, -9, -hip * .35),
        "right_foot": (-(38 - knee) * .5, 5, hip * .35),
        "left_arm": (-24, -12, -22 + arm * .4),
        "right_arm": (-13, 18, 15 - arm * .3),
        "left_forearm": (-34, -8, -6), "right_forearm": (-24, 8, 6),
        "left_blade": (-3, -12, -9), "right_blade": (3, 8, 6),
        "tail_or_rear_spine": (12, -hip * .5, 7),
    }, position={"root": (offset, -.35, 0)})))
clip("strafe_left", .72, strafe_frames, True)
clip("strafe_right", .72, [(t, mirror(p)) for t, p in strafe_frames], True)

# A right-hand diagonal slash is wound through the waist, shoulder and blade,
# with a fast contact interval and a visibly longer inertial followthrough.
slash_wind = pose({
    "body": (4, -4, -3), "waist": (-3, 22, 3), "chest": (13, 31, -8),
    "head": (-7, -30, 4), "right_shoulder": (-18, 20, 28),
    "right_arm": (-72, 25, 44), "right_forearm": (-46, -18, 15),
    "right_blade": (9, 16, 14), "right_hand": (-18, 12, 12),
    "left_arm": (-24, -14, -16), "left_forearm": (-46, 0, -7),
    "left_leg": (-11, 0, -7), "right_leg": (13, 0, 7),
    "right_lower_leg": (23, 0, 0), "tail_or_rear_spine": (12, -16, 3),
}, position={"root": (0, -.6, .1)})
slash_contact = pose({
    "body": (8, -7, 6), "waist": (-4, -26, -4), "chest": (23, -38, 10),
    "head": (-13, 38, -7), "right_shoulder": (-8, -22, 10),
    "right_arm": (-53, -68, -65), "right_forearm": (-12, -15, -16),
    "right_blade": (-11, -22, -25), "right_hand": (12, -14, -11),
    "left_arm": (14, -7, -24), "left_forearm": (-28, -9, -12),
    "left_leg": (-23, 3, -9), "right_leg": (22, -3, 7),
    "left_lower_leg": (20, 0, 0), "right_lower_leg": (15, 0, 0),
    "tail_or_rear_spine": (8, 23, -5),
}, position={"root": (0, -.4, -.6)})
slash_over = pose({
    "body": (7, -9, 7), "waist": (-3, -33, -4), "chest": (25, -48, 12),
    "head": (-16, 43, -6), "right_arm": (-31, -73, -87),
    "right_forearm": (-17, -11, -22), "right_blade": (-15, -21, -31),
    "left_arm": (20, -10, -27), "left_leg": (-17, 0, -8),
    "right_leg": (18, 0, 8), "tail_or_rear_spine": (10, 28, -6),
}, position={"root": (0, -.2, -.35)})
slash_reset = pose({"chest": (15, -9, 2), "waist": (-3, -5, 0),
                    "right_arm": (-15, -10, -14), "right_forearm": (-31, 0, -4),
                    "right_blade": (-4, -7, -8)})
slash = [(0, pose()), (.16, slash_wind), (.31, slash_wind), (.40, slash_contact),
         (.46, slash_over), (.51, slash_reset), (.55, pose())]
clip("melee_right", .55, slash)
clip("melee_left", .55, [(t, mirror(p)) for t, p in slash])

# Server damage is at total elapsed .50/1.10/1.70 s. The followup clips begin
# at .70/1.30 s, so their contact keys are exactly .40 s into each new clip.
# The second starts in the first's overshoot to avoid a neutral-pose pop.
clip("blade_combo_1", .7, [(0, pose()), (.20, slash_wind), (.39, slash_wind),
                          (.50, slash_contact), (.59, slash_over), (.7, slash_over)])
clip("blade_combo_2", .6, [(0, slash_over), (.20, mirror(slash_wind)),
                          (.31, mirror(slash_wind)), (.40, mirror(slash_contact)),
                          (.51, mirror(slash_over)), (.6, mirror(slash_over))])
overhead = pose({
    "body": (-5, 0, 0), "waist": (3, 6, 0), "chest": (-12, -6, 0),
    "neck": (3, 0, 0), "head": (-5, 0, 0),
    "left_shoulder": (-12, -10, -13), "right_shoulder": (-12, 10, 13),
    "left_arm": (-159, -9, -19), "right_arm": (-155, 9, 19),
    "left_forearm": (-21, 0, -9), "right_forearm": (-21, 0, 9),
    "left_blade": (15, -11, -7), "right_blade": (15, 11, 7),
    "left_leg": (-17, 0, -9), "right_leg": (-17, 0, 9),
    "left_lower_leg": (23, 0, 0), "right_lower_leg": (23, 0, 0),
    "back_spines": (-18, 0, 0), "tail_or_rear_spine": (16, 0, 0),
}, position={"root": (0, -.3, 0)})
downcut = pose({
    "body": (15, 0, 0), "waist": (-7, 0, 0), "chest": (35, 0, 0),
    "neck": (-16, 0, 0), "head": (-15, 0, 0),
    "left_arm": (-43, -15, -17), "right_arm": (-39, 15, 17),
    "left_forearm": (-6, 0, -5), "right_forearm": (-6, 0, 5),
    "left_blade": (-24, 0, -4), "right_blade": (-24, 0, 4),
    "left_leg": (-32, 0, -12), "right_leg": (-32, 0, 12),
    "left_lower_leg": (42, 0, 0), "right_lower_leg": (42, 0, 0),
    "left_foot": (-15, 0, 0), "right_foot": (-15, 0, 0),
    "tail_or_rear_spine": (26, 0, 0), "back_spines": (-23, 0, 0),
}, position={"root": (0, -2.2, -.7)})
clip("blade_combo_3", .65, [(0, mirror(slash_over)), (.22, overhead),
                          (.32, overhead), (.40, downcut), (.51, downcut),
                          (.65, pose({"chest": (25, 0, 0), "left_arm": (-26, -5, -11),
                                     "right_arm": (-26, 5, 11)}, position={"root": (0, -.7, 0)}))])

crouch = pose({
    "body": (9, 0, 0), "waist": (-6, 0, 0), "chest": (28, 0, 0),
    "neck": (-15, 0, 0), "head": (-13, 0, 0),
    "left_leg": (-43, 0, -10), "right_leg": (-43, 0, 10),
    "left_lower_leg": (57, 0, 0), "right_lower_leg": (57, 0, 0),
    "left_foot": (-17, 0, 0), "right_foot": (-17, 0, 0),
    "left_arm": (24, -18, -19), "right_arm": (24, 18, 19),
    "left_forearm": (-53, -4, -6), "right_forearm": (-53, 4, 6),
    "left_blade": (11, -9, -8), "right_blade": (11, 9, 8),
    "back_spines": (-18, 0, 0), "tail_or_rear_spine": (22, 0, 0),
}, position={"root": (0, -3.5, .8)})
clip("charge_prepare", .7, [(0, pose()), (.16, pose({"head": (-9, 0, 0),
     "left_shoulder": (-5, -8, -12), "right_shoulder": (-5, 8, 12)})),
     (.40, crouch), (.58, crouch), (.7, crouch)])
charge_frames = []
for i in range(9):
    phase = i / 8 * math.tau
    jolt = math.sin(phase)
    charge_frames.append((round(.45 * i / 8, 4), pose({
        "body": (12, 0, jolt * 2), "waist": (-7, jolt * 3, 0),
        "chest": (38, -jolt * 4, -jolt), "neck": (-22, 0, 0), "head": (-18, 0, 0),
        "left_arm": (30, -16, -16), "right_arm": (30, 16, 16),
        "left_forearm": (-61, 0, -6), "right_forearm": (-61, 0, 6),
        "left_blade": (10, -7, -7), "right_blade": (10, 7, 7),
        "left_leg": (-18 - jolt * 29, 0, -7), "right_leg": (-18 + jolt * 29, 0, 7),
        "left_lower_leg": (29 + jolt * 19, 0, 0), "right_lower_leg": (29 - jolt * 19, 0, 0),
        "left_foot": (-12 - jolt * 16, 0, 0), "right_foot": (-12 + jolt * 16, 0, 0),
        "back_spines": (-23, 0, 0), "tail_or_rear_spine": (24, -jolt * 4, 0),
    }, position={"root": (0, -2.7 + abs(jolt) * .4, 0)})))
clip("charge", .45, charge_frames, True)
brake = pose({
    "body": (-8, 0, 4), "waist": (3, 6, -3), "chest": (9, -10, 4),
    "head": (-3, 8, 0), "left_arm": (-31, -28, -44), "right_arm": (-27, 22, 41),
    "left_forearm": (-19, 0, -5), "right_forearm": (-19, 0, 5),
    "left_leg": (-36, 0, -8), "right_leg": (21, 0, 8),
    "left_lower_leg": (24, 0, 0), "right_lower_leg": (19, 0, 0),
    "left_foot": (9, 0, 0), "right_foot": (-12, 0, 0),
    "tail_or_rear_spine": (8, 15, -4),
}, position={"root": (0, -1.5, .9)})
clip("charge_end", .4, [(0, charge_frames[-1][1]), (.09, brake), (.17, brake),
                        (.29, pose({"chest": (17, -4, 1), "left_arm": (-17, -16, -21),
                                     "right_arm": (-17, 12, 21)})), (.4, pose())])

clip("jump_prepare", .6, [(0, pose()), (.15, pose({"left_arm": (18, -12, -15),
    "right_arm": (18, 12, 15), "chest": (21, 0, 0)})), (.36, crouch), (.5, crouch),
    (.6, pose({"body": (-3, 0, 0), "chest": (7, 0, 0), "left_arm": (-64, -12, -21),
              "right_arm": (-64, 12, 21), "left_leg": (17, 0, -8), "right_leg": (17, 0, 8),
              "left_lower_leg": (11, 0, 0), "right_lower_leg": (11, 0, 0)},
              position={"root": (0, .7, 0)}))])
tuck = pose({
    "body": (11, 0, 0), "waist": (-6, 0, 0), "chest": (6, 0, 0), "head": (-9, 0, 0),
    "left_arm": (-124, -12, -27), "right_arm": (-124, 12, 27),
    "left_forearm": (-37, 0, -7), "right_forearm": (-37, 0, 7),
    "left_blade": (13, -10, -9), "right_blade": (13, 10, 9),
    "left_leg": (-72, 0, -10), "right_leg": (-58, 0, 10),
    "left_lower_leg": (83, 0, 0), "right_lower_leg": (76, 0, 0),
    "left_foot": (-23, 0, 0), "right_foot": (-27, 0, 0),
    "back_spines": (-17, 0, 0), "tail_or_rear_spine": (30, 0, 0),
})
clip("jump", .85, [(0, pose({"chest": (8, 0, 0), "left_arm": (-65, -12, -21),
                            "right_arm": (-65, 12, 21)})), (.17, tuck), (.38, tuck),
                    (.61, overhead), (.85, overhead)], "hold_on_last_frame")
clip("slam", .6, [(0, overhead), (.09, downcut), (.18, downcut),
                  (.28, pose({"body": (11, 0, 0), "chest": (31, 0, 0),
                              "left_arm": (-28, -16, -25), "right_arm": (-28, 16, 25),
                              "left_leg": (-28, 0, -12), "right_leg": (-28, 0, 12),
                              "left_lower_leg": (38, 0, 0), "right_lower_leg": (38, 0, 0),
                              "back_spines": (-21, 0, 0)}, position={"root": (0, -1.6, 0)})),
                  (.46, pose({"chest": (18, 0, 0)}, position={"root": (0, -.3, 0)})),
                  (.6, pose())])

dodge_load = pose({
    "body": (5, 0, 13), "waist": (-4, -17, -13), "chest": (18, 19, 14),
    "head": (-10, -17, -11), "left_arm": (-37, -13, -29),
    "right_arm": (4, 15, 39), "left_forearm": (-45, 0, -9),
    "right_forearm": (-21, 0, 7), "left_leg": (-29, 0, -17),
    "right_leg": (-21, 0, 9), "left_lower_leg": (38, 0, 0),
    "right_lower_leg": (31, 0, 0), "back_spines": (-14, 0, 0),
    "tail_or_rear_spine": (15, -17, -9),
}, position={"root": (-.7, -2, 0)})
dodge_flight = pose({
    "body": (13, 6, -22), "waist": (-6, 19, 18), "chest": (25, -21, -17),
    "neck": (-14, 8, 6), "head": (-10, 13, 9),
    "left_arm": (-18, -27, -54), "right_arm": (-51, 19, 12),
    "left_forearm": (-19, 0, -11), "right_forearm": (-43, 0, 9),
    "left_blade": (-9, -14, -12), "right_blade": (8, 16, 12),
    "left_leg": (-18, 0, -32), "right_leg": (14, 0, 17),
    "left_lower_leg": (22, 0, 0), "right_lower_leg": (43, 0, 0),
    "left_foot": (-7, -8, 8), "right_foot": (-29, 13, -7),
    "tail_or_rear_spine": (22, 21, 12),
}, position={"root": (2.3, -1.1, -.1)})
dodge = [(0, pose()), (.07, dodge_load), (.17, dodge_flight), (.25, dodge_flight),
         (.33, pose({"body": (5, 0, -7), "waist": (-3, 8, 5), "chest": (15, -8, -4),
                     "left_arm": (-20, -15, -25), "right_arm": (-22, 15, 17)},
                     position={"root": (.7, -.7, 0)})), (.4, pose())]
clip("dodge_left", .4, dodge)
clip("dodge_right", .4, [(t, mirror(p)) for t, p in dodge])
clip("backstep", .42, [(0, pose()), (.08, pose({"body": (7, 0, 0), "chest": (23, 0, 0),
    "left_leg": (-32, 0, -8), "right_leg": (-32, 0, 8),
    "left_lower_leg": (42, 0, 0), "right_lower_leg": (42, 0, 0),
    "left_arm": (-39, -9, -18), "right_arm": (-39, 9, 18)}, position={"root": (0, -1.8, 0)})),
    (.18, pose({"body": (-13, 0, 0), "chest": (12, 0, 0), "head": (-1, 0, 0),
               "left_arm": (-61, -12, -24), "right_arm": (-61, 12, 24),
               "left_leg": (-19, 0, -7), "right_leg": (21, 0, 7),
               "left_lower_leg": (47, 0, 0), "right_lower_leg": (19, 0, 0),
               "tail_or_rear_spine": (33, 0, 0)}, position={"root": (0, .5, .6)})),
    (.29, brake), (.42, pose())])

# Warp contracts armor and body before disappearance. The end clip unfolds
# from exactly the same small silhouette and spreads the forearm blades.
warp_closed = pose({
    "body": (16, 0, 0), "waist": (-9, 0, 0), "chest": (39, 0, 0),
    "neck": (12, 0, 0), "head": (18, 0, 0),
    "left_shoulder": (16, 19, 24), "right_shoulder": (16, -19, -24),
    "left_arm": (-82, 43, 42), "right_arm": (-82, -43, -42),
    "left_forearm": (-84, 16, 29), "right_forearm": (-84, -16, -29),
    "left_blade": (22, 19, 21), "right_blade": (22, -19, -21),
    "left_leg": (-55, 0, -4), "right_leg": (-55, 0, 4),
    "left_lower_leg": (71, 0, 0), "right_lower_leg": (71, 0, 0),
    "left_foot": (-19, 0, 0), "right_foot": (-19, 0, 0),
    "back_spines": (13, 0, 0), "back_blade_left": (8, 12, 17),
    "back_blade_right": (8, -12, -17), "tail_or_rear_spine": (-11, 0, 0),
}, position={"root": (0, -4.2, 0)})
warp_open = pose({
    "body": (1, -9, -2), "chest": (17, -18, -4), "head": (-10, 25, 1),
    "left_arm": (-31, -28, -63), "right_arm": (-31, 28, 63),
    "left_forearm": (-18, -7, -16), "right_forearm": (-18, 7, 16),
    "left_blade": (-8, -14, -12), "right_blade": (-8, 14, 12),
    "left_leg": (-14, 0, -12), "right_leg": (-14, 0, 12),
    "left_lower_leg": (22, 0, 0), "right_lower_leg": (22, 0, 0),
    "back_spines": (-19, 0, 0), "back_blade_left": (-9, -16, -18),
    "back_blade_right": (-9, 16, 18), "tail_or_rear_spine": (21, 0, 0),
}, position={"root": (0, -.6, 0)})
clip("teleport_start", .6, [(0, pose()), (.19, crouch), (.43, warp_closed), (.6, warp_closed)])
clip("teleport_end", .3, [(0, warp_closed), (.07, warp_open), (.14, warp_open),
                          (.23, pose({"chest": (19, -7, -2), "left_arm": (-18, -15, -32),
                                     "right_arm": (-18, 15, 32)})), (.3, pose())])

wave_coil = pose({
    "body": (6, 0, 0), "chest": (31, 0, 0), "head": (5, 0, 0),
    "left_shoulder": (8, 10, -15), "right_shoulder": (8, -10, 15),
    "left_arm": (-75, 24, 13), "right_arm": (-75, -24, -13),
    "left_forearm": (-82, 17, 23), "right_forearm": (-82, -17, -23),
    "left_hand": (-24, 0, 17), "right_hand": (-24, 0, -17),
    "left_leg": (-28, 0, -13), "right_leg": (-28, 0, 13),
    "left_lower_leg": (37, 0, 0), "right_lower_leg": (37, 0, 0),
    "back_spines": (-24, 0, 0), "tail_or_rear_spine": (19, 0, 0),
}, position={"root": (0, -1.7, 0)})
wave_release = pose({
    "body": (-6, 0, 0), "waist": (4, 0, 0), "chest": (-14, 0, 0),
    "neck": (4, 0, 0), "head": (-13, 0, 0),
    "left_shoulder": (-16, -20, -24), "right_shoulder": (-16, 20, 24),
    "left_arm": (-31, -36, -92), "right_arm": (-31, 36, 92),
    "left_forearm": (-13, -10, -14), "right_forearm": (-13, 10, 14),
    "left_blade": (-14, -17, -19), "right_blade": (-14, 17, 19),
    "left_leg": (-8, 0, -15), "right_leg": (-8, 0, 15),
    "left_lower_leg": (12, 0, 0), "right_lower_leg": (12, 0, 0),
    "back_spines": (-29, 0, 0), "back_blade_left": (-11, -19, -23),
    "back_blade_right": (-11, 19, 23), "tail_or_rear_spine": (27, 0, 0),
}, position={"root": (0, .25, 0)})
clip("shockwave_prepare", 1.0, [(0, pose()), (.22, pose({"chest": (21, 0, 0),
    "left_arm": (-38, 13, -5), "right_arm": (-38, -13, 5),
    "left_forearm": (-61, 9, 13), "right_forearm": (-61, -9, -13)})),
    (.57, wave_coil), (.83, wave_coil), (1.0, wave_coil)])
clip("shockwave_attack", .5, [(0, wave_coil), (.10, wave_release), (.19, wave_release),
    (.35, pose({"chest": (2, 0, 0), "left_arm": (-19, -23, -55),
               "right_arm": (-19, 23, 55), "back_spines": (-17, 0, 0)})), (.5, pose())])

# Ranged charge presents the right forearm's blade groove as a focusing rail.
aim = pose({
    "body": (3, -3, 0), "waist": (-3, 12, -3), "chest": (14, -27, 3),
    "head": (-8, 19, -2), "right_shoulder": (-9, -13, 4),
    "right_arm": (-81, -18, 8), "right_forearm": (-61, 16, 3),
    "right_blade": (-13, 0, -6), "right_hand": (-21, 0, 5),
    "left_arm": (-52, 31, 22), "left_forearm": (-87, 13, 26),
    "left_hand": (-29, 0, 19), "left_leg": (-9, 0, -7),
    "right_leg": (10, 0, 7), "right_lower_leg": (17, 0, 0),
    "back_spines": (-16, 0, 0), "tail_or_rear_spine": (13, 9, 0),
})
fire = pose({
    "body": (-5, 0, -4), "waist": (1, 14, 2), "chest": (5, -34, -3),
    "head": (-3, 23, 1), "right_shoulder": (-6, -17, 0),
    "right_arm": (-93, -9, 3), "right_forearm": (-3, 0, 0),
    "right_blade": (-19, -7, -9), "right_hand": (9, 0, 0),
    "left_arm": (-32, 22, 9), "left_forearm": (-43, 0, 12),
    "left_leg": (-11, 0, -8), "right_leg": (14, 0, 8),
    "right_lower_leg": (24, 0, 0), "tail_or_rear_spine": (6, 16, 2),
}, position={"root": (0, -.2, .7)})
clip("ranged_prepare", .7, [(0, pose()), (.22, pose({"chest": (15, -12, 1),
    "right_arm": (-46, -7, 9), "right_forearm": (-51, 0, 0),
    "left_arm": (-28, 14, 11), "left_forearm": (-48, 0, 8)})),
    (.48, aim), (.61, aim), (.7, aim)])
clip("ranged_attack", .4, [(0, aim), (.08, fire), (.16, fire),
    (.29, pose({"chest": (13, -17, 2), "right_arm": (-62, -10, 8),
               "right_forearm": (-37, 0, 3), "right_blade": (-8, -3, -4)})), (.4, pose())])

# APEX EXECUTION visibly locks into an X-shaped guard, makes three directional
# cuts, then crosses both blades in a final X before the delayed wave release.
execution_guard = pose({
    "body": (7, 0, 0), "waist": (-4, 0, 0), "chest": (22, 0, 0),
    "neck": (-9, 0, 0), "head": (-16, 0, 0),
    "left_shoulder": (-12, 17, 5), "right_shoulder": (-12, -17, -5),
    "left_arm": (-107, 39, 37), "right_arm": (-107, -39, -37),
    "left_forearm": (-39, 12, 33), "right_forearm": (-39, -12, -33),
    "left_blade": (-5, 11, 17), "right_blade": (-5, -11, -17),
    "left_hand": (-13, 0, 15), "right_hand": (-13, 0, -15),
    "left_leg": (-22, 0, -13), "right_leg": (-22, 0, 13),
    "left_lower_leg": (31, 0, 0), "right_lower_leg": (31, 0, 0),
    "back_spines": (-35, 0, 0), "back_blade_left": (-21, -33, -41),
    "back_blade_right": (-21, 33, 41), "tail_or_rear_spine": (29, 0, 0),
    "face_plate": (12, 0, 0),
}, position={"root": (0, -1.3, 0)}, rage=True)
execution_open = pose({
    "body": (6, 0, 0), "chest": (21, 0, 0), "head": (-14, 0, 0),
    "left_arm": (-57, -43, -102), "right_arm": (-57, 43, 102),
    "left_forearm": (-9, -16, -21), "right_forearm": (-9, 16, 21),
    "left_blade": (-16, -18, -26), "right_blade": (-16, 18, 26),
    "left_leg": (-25, 0, -16), "right_leg": (-25, 0, 16),
    "left_lower_leg": (35, 0, 0), "right_lower_leg": (35, 0, 0),
    "back_spines": (-39, 0, 0), "back_blade_left": (-24, -36, -45),
    "back_blade_right": (-24, 36, 45), "tail_or_rear_spine": (32, 0, 0),
    "face_plate": (13, 0, 0),
}, position={"root": (0, -1.7, -.4)}, rage=True)
clip("special_prepare", 1.8, [(0, pose(rage=True)), (.25, pose({
    "left_arm": (-64, 13, 9), "right_arm": (-64, -13, -9),
    "left_forearm": (-47, 9, 13), "right_forearm": (-47, -9, -13),
    "head": (-17, 0, 0)}, rage=True)), (.63, execution_guard),
    (1.1, execution_guard), (1.45, pose({"chest": (25, 0, 0), "head": (-18, 0, 0),
    "left_arm": (-108, 39, 37), "right_arm": (-108, -39, -37),
    "left_forearm": (-42, 12, 33), "right_forearm": (-42, -12, -33),
    "back_spines": (-38, 0, 0), "back_blade_left": (-23, -36, -44),
    "back_blade_right": (-23, 36, 44)}, position={"root": (0, -1.6, 0)}, rage=True)),
    (1.8, execution_guard)])
execution_cross = pose({
    "body": (13, 0, 0), "chest": (34, 0, 0), "head": (-21, 0, 0),
    "left_arm": (-84, 49, 48), "right_arm": (-84, -49, -48),
    "left_forearm": (-9, 17, 28), "right_forearm": (-9, -17, -28),
    "left_blade": (-19, 17, 21), "right_blade": (-19, -17, -21),
    "left_leg": (-32, 0, -17), "right_leg": (-32, 0, 17),
    "left_lower_leg": (42, 0, 0), "right_lower_leg": (42, 0, 0),
    "back_spines": (-41, 0, 0), "back_blade_left": (-26, -39, -47),
    "back_blade_right": (-26, 39, 47), "tail_or_rear_spine": (35, 0, 0),
}, position={"root": (0, -2.1, -.7)}, rage=True)


def deployed(source):
    """Execution attacks never fold phase-3 armor between directional cuts."""
    result = {channel: {bone: list(v) for bone, v in tracks.items()} for channel, tracks in source.items()}
    for bone in ("back_spines", "back_blade_left", "back_blade_right", "tail_or_rear_spine", "face_plate"):
        result["rotation"][bone] = list(RAGE[bone])
    return result


# Contacts match the server: dash cuts at 0/.60/1.20, return at 1.80,
# final X at 2.00 and delayed outward energy release at 2.60 seconds.
clip("special_attack", 2.9, [(0, deployed(slash_contact)), (.17, deployed(slash_over)),
    (.38, deployed(mirror(slash_wind))), (.50, deployed(mirror(slash_wind))),
    (.60, deployed(mirror(slash_contact))), (.77, deployed(mirror(slash_over))),
    (1.01, deployed(overhead)), (1.12, deployed(overhead)), (1.20, deployed(downcut)),
    (1.39, deployed(downcut)), (1.60, execution_guard), (1.80, execution_open),
    (1.90, execution_open), (2.0, execution_cross), (2.13, execution_cross),
    (2.31, execution_open), (2.48, deployed(wave_coil)),
    (2.60, deployed(wave_release)), (2.73, deployed(wave_release)), (2.9, pose(rage=True))])

# Both transitions share the deployment clip. Its last pose leaves armor
# expanded; rage locomotion keeps the full expansion after phase 3.
roar = pose({
    "body": (-7, 0, 0), "waist": (5, 0, 0), "chest": (-19, 0, 0),
    "neck": (6, 0, 0), "head": (-27, 0, 0), "face_plate": (24, 0, 0),
    "left_shoulder": (-14, -17, -25), "right_shoulder": (-14, 17, 25),
    "left_arm": (-24, -27, -58), "right_arm": (-24, 27, 58),
    "left_forearm": (-29, -13, -19), "right_forearm": (-29, 13, 19),
    "left_blade": (-9, -16, -15), "right_blade": (-9, 16, 15),
    "left_leg": (-11, 0, -16), "right_leg": (-11, 0, 16),
    "left_lower_leg": (19, 0, 0), "right_lower_leg": (19, 0, 0),
    "back_spines": (-43, 0, 0), "back_blade_left": (-27, -39, -47),
    "back_blade_right": (-27, 39, 47), "tail_or_rear_spine": (36, 0, 0),
}, position={"root": (0, -.4, 0)}, rage=True)
clip("phase_change", 1.8, [(0, pose()), (.18, wave_coil), (.4, wave_coil),
    (.63, pose({"body": (-3, 0, 0), "chest": (-9, 0, 0), "head": (-18, 0, 0),
               "face_plate": (15, 0, 0), "left_arm": (-16, -21, -39),
               "right_arm": (-16, 21, 39), "back_spines": (-24, 0, 0),
               "back_blade_left": (-16, -25, -33),
               "back_blade_right": (-16, 25, 33)}, rage=True)),
    (.9, roar), (1.08, roar), (1.25, pose({"body": (-5, 0, -2), "chest": (-15, 0, 2),
        "head": (-23, -3, 0), "face_plate": (21, 0, 0), "left_arm": (-24, -27, -53),
        "right_arm": (-24, 27, 53), "back_spines": (-41, 0, 0),
        "back_blade_left": (-25, -37, -45), "back_blade_right": (-25, 37, 45)}, rage=True)),
    (1.5, pose({"chest": (6, 0, 0), "head": (-13, 0, 0), "face_plate": (13, 0, 0),
               "left_arm": (-18, -19, -30), "right_arm": (-18, 19, 30)}, rage=True)),
    (1.8, pose(rage=True))])

light_hurt = pose({"body": (-4, 0, -3), "waist": (1, -5, 2),
    "chest": (-3, 8, -6), "head": (-12, -8, 4),
    "left_shoulder": (4, -8, -14), "right_shoulder": (2, 8, 14),
    "left_arm": (-19, -7, -23), "right_arm": (-12, 7, 24),
    "back_spines": (1, 0, 0), "tail_or_rear_spine": (9, 8, 0)})
clip("hurt_light", .28, [(0, pose()), (.055, light_hurt), (.11, light_hurt),
                        (.21, pose({"chest": (12, 3, -1), "head": (-7, -3, 1)})), (.28, pose())])
heavy_hurt = pose({
    "body": (-15, 0, -7), "waist": (5, -13, 5), "chest": (-17, 23, -13),
    "neck": (4, -8, 3), "head": (-19, -18, 9), "face_plate": (9, 0, 0),
    "left_arm": (-31, -17, -51), "right_arm": (-21, 22, 39),
    "left_forearm": (-41, 0, -19), "right_forearm": (-31, 0, 13),
    "left_blade": (14, -17, -18), "right_blade": (12, 13, 11),
    "left_leg": (-24, 0, -11), "right_leg": (9, 0, 11),
    "left_lower_leg": (32, 0, 0), "right_lower_leg": (19, 0, 0),
    "tail_or_rear_spine": (-4, 21, 6), "back_spines": (5, 0, 0),
}, position={"root": (0, -1, .5)})
clip("hurt_heavy", .55, [(0, pose()), (.09, heavy_hurt), (.20, heavy_hurt),
    (.33, pose({"body": (2, 0, -2), "chest": (20, 9, -4),
               "head": (3, -9, 3), "left_arm": (-18, -12, -27),
               "right_arm": (-18, 12, 27)}, position={"root": (0, -.7, .1)})), (.55, pose())])
stagger_pose = pose({
    "body": (14, 0, 7), "waist": (-8, 12, -6), "chest": (34, -14, 9),
    "neck": (5, 5, 0), "head": (17, 7, -3),
    "left_arm": (7, -12, -17), "right_arm": (-19, 7, 7),
    "left_forearm": (-12, 0, -5), "right_forearm": (-64, 9, 23),
    "left_blade": (12, -9, -13), "right_blade": (-5, 5, 7),
    "left_leg": (-36, 0, -12), "right_leg": (-17, 0, 6),
    "left_lower_leg": (49, 0, 0), "right_lower_leg": (29, 0, 0),
    "left_foot": (-17, 0, 0), "right_foot": (-12, 0, 0),
    "back_spines": (6, 0, 0), "tail_or_rear_spine": (0, -13, -5),
}, position={"root": (0, -2.7, 0)})
clip("stagger", .8, [(0, light_hurt), (.16, stagger_pose), (.34, stagger_pose),
    (.53, pose({"body": (10, 0, 5), "waist": (-6, 9, -4), "chest": (28, -11, 6),
               "head": (9, 8, -2), "left_arm": (4, -10, -19),
               "right_arm": (-21, 8, 9), "left_leg": (-28, 0, -9),
               "left_lower_leg": (37, 0, 0)}, position={"root": (0, -1.8, 0)})),
    (.8, pose())])
clip("recovery", .6, [(0, pose({"body": (8, 0, 0), "chest": (24, 0, 0),
    "head": (1, 0, 0), "left_arm": (7, -9, -9), "right_arm": (7, 9, 9),
    "left_forearm": (-9, 0, -3), "right_forearm": (-9, 0, 3),
    "left_leg": (-16, 0, -5), "right_leg": (-16, 0, 5),
    "left_lower_leg": (24, 0, 0), "right_lower_leg": (24, 0, 0)},
    position={"root": (0, -.9, 0)})), (.2, pose({"chest": (18, 0, 0),
    "head": (-3, 0, 0), "left_arm": (-3, -9, -11), "right_arm": (-3, 9, 11)},
    position={"root": (0, -.35, 0)})), (.42, pose({"chest": (11, 0, 0)})), (.6, pose())])

# Death is a three-stage loss of support: knees buckle, torso tips forward,
# then armor settles with a small side roll. A held final frame remains a
# grounded corpse until server despawn; root lift compensates the rotated mesh.
dead_kneel = pose({
    "body": (17, 0, 9), "waist": (-7, -5, -4), "chest": (37, 11, 4),
    "neck": (13, -6, 0), "head": (25, -8, -3), "face_plate": (12, 0, 0),
    "left_arm": (11, -9, -18), "right_arm": (4, 8, 21),
    "left_forearm": (-8, 0, -9), "right_forearm": (-18, 0, 9),
    "left_leg": (-68, 0, -13), "right_leg": (-54, 0, 13),
    "left_lower_leg": (108, 0, 0), "right_lower_leg": (96, 0, 0),
    "left_foot": (-37, 0, 0), "right_foot": (-34, 0, 0),
    "back_spines": (11, 0, 0), "back_blade_left": (7, 4, 11),
    "back_blade_right": (7, -4, -11), "tail_or_rear_spine": (-9, 11, -5),
}, position={"root": (0, -7.5, .9)})
dead_fall = pose({
    "root": (60, 0, -16), "body": (9, 4, 5), "waist": (-4, -3, -3),
    "chest": (12, -5, 3), "neck": (9, 7, 6), "head": (19, 17, 7),
    "face_plate": (9, 0, 0), "left_arm": (-12, -9, -18),
    "right_arm": (3, 13, 16), "left_forearm": (-13, 0, -11),
    "right_forearm": (-41, 0, 17), "left_blade": (11, -5, -9),
    "right_blade": (13, 9, 17), "left_leg": (-43, 7, -13),
    "right_leg": (-31, -11, 13), "left_lower_leg": (69, 0, -9),
    "right_lower_leg": (58, 0, 11), "left_foot": (-27, 0, 0),
    "right_foot": (-21, 0, 0), "back_spines": (16, 0, 0),
    "back_blade_left": (11, 5, 13), "back_blade_right": (11, -5, -13),
    "tail_or_rear_spine": (-16, 19, -9),
}, position={"root": (1, 4.34, .9)})
dead_rest = pose({
    "root": (85, 0, -12), "body": (-4, 0, 4), "waist": (4, 0, 0),
    "chest": (3, 0, -3), "neck": (-3, 0, 0), "head": (9, 8, -5),
    "face_plate": (7, 0, 0), "left_arm": (-8, -8, -10),
    "right_arm": (-5, 8, 10), "left_forearm": (-17, 0, -7),
    "right_forearm": (-39, 0, 12), "left_blade": (13, -7, -11),
    "right_blade": (16, 11, 19), "left_leg": (-9, 3, -6),
    "right_leg": (-21, -5, 7), "left_lower_leg": (19, 0, -3),
    "right_lower_leg": (35, 0, 4), "left_foot": (-12, 0, 0),
    "right_foot": (-17, 0, 0), "back_spines": (19, 0, 0),
    "back_blade_left": (13, 7, 17), "back_blade_right": (13, -7, -17),
    "tail_or_rear_spine": (-19, 23, -11),
}, position={"root": (1, 10.13, 1.2)})
clip("death", 2.0, [(0, pose()), (.14, heavy_hurt), (.35, stagger_pose),
    (.63, dead_kneel), (.86, dead_kneel), (1.15, dead_fall),
    (1.32, dead_rest), (1.48, dead_fall), (1.67, dead_rest), (2.0, dead_rest)],
    "hold_on_last_frame")

# Rage attack uses a lunging, two-blade scissor motion instead of reusing the
# measured phase-1 slash. The open shoulders snap inward, then recoil hard.
rage_wind = pose({
    "body": (11, 0, -5), "waist": (-7, 17, 4), "chest": (31, -21, -8),
    "head": (-17, 17, 3), "left_arm": (-89, -27, -87),
    "right_arm": (-79, 33, 91), "left_forearm": (-23, -9, -11),
    "right_forearm": (-27, 9, 11), "left_blade": (17, -19, -17),
    "right_blade": (17, 19, 17), "left_leg": (-33, 0, -14),
    "right_leg": (-17, 0, 11), "left_lower_leg": (44, 0, 0),
    "right_lower_leg": (29, 0, 0), "tail_or_rear_spine": (33, -19, 6),
}, position={"root": (0, -1.9, .3)}, rage=True)
rage_contact = pose({
    "body": (16, 0, 6), "waist": (-7, -16, -4), "chest": (39, 21, 7),
    "head": (-23, -19, -3), "left_arm": (-74, 41, 43),
    "right_arm": (-84, -37, -47), "left_forearm": (-11, 13, 23),
    "right_forearm": (-9, -13, -23), "left_blade": (-21, 17, 21),
    "right_blade": (-21, -17, -21), "left_leg": (-43, 0, -15),
    "right_leg": (19, 0, 9), "left_lower_leg": (51, 0, 0),
    "right_lower_leg": (18, 0, 0), "tail_or_rear_spine": (36, 21, -9),
}, position={"root": (0, -2.1, -.9)}, rage=True)
clip("rage_attack", .35, [(0, pose(rage=True)), (.07, rage_wind), (.14, rage_wind),
    (.20, rage_contact), (.25, rage_contact), (.30, execution_open), (.35, pose(rage=True))])

EXPECTED = {
    "idle", "walk", "run", "sprint", "strafe_left", "strafe_right", "melee_left", "melee_right",
    "blade_combo_1", "blade_combo_2", "blade_combo_3", "charge_prepare", "charge", "charge_end",
    "jump_prepare", "jump", "slam", "dodge_left", "dodge_right", "backstep", "teleport_start",
    "teleport_end", "shockwave_prepare", "shockwave_attack", "ranged_prepare", "ranged_attack",
    "special_prepare", "special_attack", "phase_change", "hurt_light", "hurt_heavy", "stagger",
    "recovery", "death", "rage_idle", "rage_run", "rage_attack",
}
assert {name.removeprefix("animation.apex.") for name in ANIMATIONS} == EXPECTED
assert all(set(animation["bones"]) == set(BONES) for animation in ANIMATIONS.values())
geometry_path = ROOT / "src/main/resources/assets/apexpredator/geo/apex.geo.json"
if geometry_path.exists():
    geometry = json.loads(geometry_path.read_text())["minecraft:geometry"][0]
    model_bones = {bone["name"] for bone in geometry["bones"]}
    assert set(BONES).issubset(model_bones), f"Animation bones absent from model: {set(BONES) - model_bones}"

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_text(json.dumps({"format_version": "1.8.0", "animations": ANIMATIONS}, indent=2) + "\n")
print(f"Wrote {len(ANIMATIONS)} authored clips to {OUTPUT}")
for name, animation in ANIMATIONS.items():
    print(f"{name}: {animation['animation_length']:g}s, loop={animation['loop']}")

#!/usr/bin/env python3
"""Generate additive Phase 3 healing resources without changing existing Apex assets.

Python + numpy/Pillow + ffmpeg. The mask is a subset of the existing emissive atlas:
chest strongest, neck secondary, waist/limb seams dim, no eyes, blades or back fins.
The two original synthesized mono Vorbis samples are quiet and finite, never loops.
"""
from pathlib import Path
import json
import subprocess
import tempfile
import wave

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/apexpredator"
RATE = 44100
WEIGHTS = {
    "chest": 1.0,
    "neck": .52,
    "waist": .30,
    "left_arm": .25,
    "right_arm": .25,
    "left_forearm": .22,
    "right_forearm": .22,
    "left_leg": .20,
    "right_leg": .20,
    "left_lower_leg": .18,
    "right_lower_leg": .18,
}


def healing_mask():
    geometry = json.loads((ASSETS / "geo/apex.geo.json").read_text())["minecraft:geometry"][0]
    source = np.asarray(Image.open(ASSETS / "textures/entity/apex_glow.png").convert("RGBA"))
    output = np.zeros_like(source)
    for bone in geometry["bones"]:
        weight = WEIGHTS.get(bone["name"], 0)
        if not weight:
            continue
        for cube in bone.get("cubes", []):
            for face in cube["uv"].values():
                x, y = face["uv"]
                width, height = face["uv_size"]
                x0, x1 = sorted((int(x), int(x + width)))
                y0, y1 = sorted((int(y), int(y + height)))
                existing = source[y0:y1, x0:x1]
                destination = output[y0:y1, x0:x1]
                lit = existing[:, :, 3] > 0
                # Amber energy brightens the core instead of adding emissive armor area.
                # All original atlas pixels remain untouched in their original file.
                amber = existing[:, :, :3].astype(float)
                amber[:, :, 1] = np.maximum(amber[:, :, 1], 112)
                amber[:, :, 2] = np.maximum(amber[:, :, 2], 20)
                destination[lit, :3] = np.clip(amber[lit] * weight, 0, 255).astype(np.uint8)
                destination[lit, 3] = existing[lit, 3]
    assert np.all((output[:, :, 3] == 0) | (source[:, :, 3] != 0)), "Healing mask escaped original seams"
    Image.fromarray(output).save(ASSETS / "textures/entity/apex_healing_glow.png")
    return int(np.count_nonzero(output[:, :, 3]))


def samples(name, duration):
    t = np.arange(round(RATE * duration)) / RATE
    if name == "heal_entry":
        # A short rising reactor chirp with a restrained low mechanical overtone.
        envelope = np.sin(np.pi * t / duration) ** 1.2
        signal = .25 * np.sin(2 * np.pi * (160 * t + 170 * t * t))
        signal += .11 * np.sin(2 * np.pi * 96 * t)
        signal += .05 * np.sin(2 * np.pi * (510 * t + 210 * t * t))
        signal *= envelope
    else:
        # Smooth finite bass pulse; no noise or sharp attacks competing with telegraphs.
        envelope = np.sin(np.pi * t / duration) ** 1.4
        modulation = .8 + .2 * np.cos(2 * np.pi * 2 * t)
        signal = (.20 * np.sin(2 * np.pi * 66 * t)
                  + .08 * np.sin(2 * np.pi * 99 * t)
                  + .035 * np.sin(2 * np.pi * 132 * t)) * envelope * modulation
    signal *= np.minimum(1, t / .015) * np.minimum(1, (duration - t) / .035)
    return signal


def healing_audio():
    folder = ASSETS / "sounds"
    folder.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="apex-healing-sounds-") as temporary:
        for name, duration in (("heal_entry", .52), ("heal_hum", 1.15)):
            source = Path(temporary) / f"{name}.wav"
            with wave.open(str(source), "wb") as wav:
                wav.setnchannels(1)
                wav.setsampwidth(2)
                wav.setframerate(RATE)
                wav.writeframes((samples(name, duration) * 32767).astype("<i2").tobytes())
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
                            "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
                            "-c:a", "libvorbis", "-q:a", "4", str(folder / f"{name}.ogg")], check=True)
    sound_file = ASSETS / "sounds.json"
    sound_definitions = json.loads(sound_file.read_text())
    for name in ("heal_entry", "heal_hum"):
        sound_definitions[name] = {
            "subtitle": f"sound.apexpredator.{name}",
            "sounds": [{"name": f"apexpredator:{name}", "stream": False}],
        }
    sound_file.write_text(json.dumps(sound_definitions, indent=2) + "\n")


if __name__ == "__main__":
    pixel_count = healing_mask()
    healing_audio()
    print(f"Generated healing mask ({pixel_count} existing emissive texels) and 2 original quiet samples.")

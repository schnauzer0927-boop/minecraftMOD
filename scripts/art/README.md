# APEX original model source

`generate_apex_model.py` is the reproducible authoring source for the 1.1.0
original boss model, its two UV-matched textures, and a four-view inspection
sheet. It uses Python 3, Pillow and NumPy. Run:

```sh
python3 scripts/art/generate_apex_model.py
```

The game needs only the committed `geo/apex.geo.json`, `textures/entity/apex.png`
and `textures/entity/apex_glow.png`. Python and its libraries are **not runtime
dependencies**. Preview images and model metrics go to ignored `build/art/`.

The model has 28 independently animated bones and 193 individually UV-mapped
cubes (1,158 quads). Its exact transformed neutral bounds are Y=0..43.2 model
pixels, or 2.7 blocks at renderer scale 1. The base and emissive atlases are
256×256. Emission is restricted to 1,254 actual eye, reactor, neck, joint,
blade-channel and dorsal-seam pixels; the rest of the glow texture is fully
transparent. Palette, material machining, geometry and atlas packing are all
deterministic.

Coordinates: feet at Y=0, front=-Z, anatomical left=+X. Bone transforms start
neutral. Individual cube rotations define facets and swept fins without
competing with animation. `body` is the body/hips control at [0,22,0.5]; `chest`
is the forward-posture control at [0,27,0]. Arm chains use the requested
`left/right_arm` and `left/right_forearm`, leg chains `left/right_leg` and
`left/right_lower_leg`. Digitigrade knees are at Y=13,Z=-1 and hocks at Y=4,Z=2.
`back_spines` owns the independently animated `back_blade_left/right` fins.

This is newly authored geometry and pixel artwork based on the written design
brief. No external model or texture is included, and an unavailable concept-art
attachment is not treated as a source that was inspected.

## Skeletal animation source

`python3 scripts/art/generate_animations.py` regenerates the 37 dedicated clips in
`assets/apexpredator/animations/apex.animation.json` with no third-party Python
packages. Locomotion loops use articulated knees/hocks, pelvis counter-motion,
lower posture, and moving blade fins. One-shot clips contain anticipation,
contact, recoil and follow-through using GeckoLib's supported vector/easing
keyframe format. Every clip resets the full skeleton to prevent pose leakage.

The combat server is the timing authority. Base melee contacts at .4 seconds;
Phase 2 plays its clip faster and rage melee contacts at .2 seconds. Combo
contacts are .5/.4/.4 seconds after their individual preparation cues. Execution
prepares for 1.8 seconds and then has slashes at 0/.6/1.2 seconds, center return at
1.8, the X slash at 2.0 and its delayed shockwave at 2.6; the clip ends at 2.9.
Death lasts 2.0 seconds and holds its grounded collapsed pose until cleanup.

Use `/apex animate <clip>` on an Apex summoned with `{NoAI:1b}` to inspect the
animation. `/apex effect <effect>` previews a server-synchronized visual cue for
resource-pack authors; it does not deal damage or change AI. Both require level 2
operator permission. The sprite/sound authoring notes are in
`../assets/README.md`.

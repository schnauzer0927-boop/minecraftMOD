# APEX model authoring source

`generate_apex_model.py` is the reproducible authoring source for the 1.2.0
appearance remake, its two UV-matched textures, three orthographic views and
four-view turnaround. It uses Python 3, Pillow and NumPy. Run:

```sh
python3 scripts/art/generate_apex_model.py
```

The game needs only the committed `geo/apex.geo.json`, `textures/entity/apex.png`
and `textures/entity/apex_glow.png`. Python and its libraries are **not runtime
dependencies**. Model metrics and enlarged atlas inspection images go to ignored
`build/art/`. Front, side, back and turnaround go to `docs/visuals/apex-1.2-*.png`.
The in-game screenshot is captured separately from the running client.

The remade model has the **same 28 bones, parent links and pivots as 1.1.0** and
290 individually UV-mapped cubes (1,740 quads), below the 300-cube budget.
Transformed neutral bounds are Y=0..43.2 model pixels: 2.7 blocks at renderer
scale 1. Both atlases are 512×512. Emission is limited to 964 texels belonging to
thin eye, sternum/abdomen/neck fissures, elbow, blade-channel and dorsal grooves;
the remainder of the glow atlas is fully transparent. Every face has its own
UV island and a one-texel base gutter. Armor receives individual value variation,
edge highlights, darker centers, shaded seams and small controlled scratches.
Palette, materials, geometry and atlas packing are deterministic.

The attached APEX concept art is the visual reference for this remake. Its
narrow pointed mask, swept crown, layered V-shaped thoracic shells, slim waist,
rearward shoulder fins, curved compound forearm blades, three claws, cascading
dorsal plates and digitigrade legs are interpreted as original GeckoLib cube
geometry. No external mesh or texture is embedded. The comparison images render
the actual authored geometry and atlas, rather than a separate illustration.

Coordinates: feet at Y=0, front=-Z, anatomical left=+X. Bone transforms remain
neutral; rotations are local to cubes. All existing named rig controls and
animation data are unchanged. Knees remain at Y=13,Z=-1 and hocks at Y=4,Z=2;
`back_spines` still owns `back_blade_left/right`. The software inspection renderer
matches GeckoLib's mirrored X and source rotation convention, equivalent in
source coordinates to Rz(-Z) Ry(Y) Rx(-X). This avoids the old preview's inverted
sweep angles while leaving the runtime model coordinates unchanged.

The existing 37 animation clips are preserved byte-for-byte for 1.2.0. Do not
regenerate the animation file as part of a visual-only model edit.

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

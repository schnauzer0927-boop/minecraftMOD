# APEX model authoring source

The official editable source is now [models/blockbench/apex.bbmodel](../../models/blockbench/apex.bbmodel). Follow the [Blockbench workflow](../../models/blockbench/README.md) to edit geometry, pivots, UVs and textures. The 37 runtime clips are preserved; geometry export does not overwrite animation data.

The generator below is retained for initial generation and backup of the original 1.3 appearance. Once the official project exists, regenerating it requires the explicit `--legacy-overwrite-model` option. Back up human edits before choosing that option.

`generate_apex_model.py` is the reproducible legacy generator for the 1.3.0
head and joint remake, its two UV-matched textures, orthographic/head views,
four-view turnaround and matching-camera 1.2-versus-1.3 comparisons.
It uses Python 3, Pillow and NumPy. Run:

```sh
python3 scripts/art/generate_apex_model.py --legacy-overwrite-model
```

The game needs only the committed `geo/apex.geo.json`, `textures/entity/apex.png`
and `textures/entity/apex_glow.png`. Python and its libraries are **not runtime
dependencies**. Model metrics and enlarged atlas inspection images go to ignored
`build/art/`. Front, side, back, head front/side/45-degree and turnaround go to
`docs/visuals/apex-1.3-*.png`. Comparison output uses the original 1.2 geometry
and both textures from commit `a993aa4142e2c23c7e3af3bfccca368cfdeed592`, or the
same files saved in `build/remake-1.3/baseline/`. Retain that Git history when
regenerating comparison images. The in-game screenshot is captured separately
from the running client.

The remade model has the **same 28 bones, parent links and pivots as 1.2.0** and
333 individually UV-mapped cubes (1,998 quads). The extra joint covers increase
the 1.2 budget of 290 cubes by 43. Transformed neutral bounds are
Y=0..43.203 model pixels: approximately 2.7 blocks at renderer scale 1.
Both atlases are 512×512. Emission is limited to 982 texels belonging to
thin eye, sternum/abdomen/neck fissures, elbow, blade-channel and dorsal grooves;
the remainder of the glow atlas is fully transparent. Every face has its own
UV island and a one-texel base gutter. Armor receives individual value variation,
edge highlights, darker centers, shaded seams and small controlled scratches.
Palette, materials, geometry and atlas packing are deterministic.

The previously attached APEX concept art is the visual reference. All 37 old
head cubes are replaced with 46 new cubes: an inverted-triangle sealed mask,
central ridge, layered forehead/cheek/side/chin armor, narrow recessed sensor
slits, tapered cranial layers and a curved rear blade. Cranial silhouette width
and height shrink by 16.40% and 16.47%; depth grows by 15.48%. These measurements
exclude the concealed head-owned cervical pivot bearing; its complete bounds
are reported separately. Internal frames, sleeves and overlapping plates bridge
the neck, shoulders, elbows, wrists, waist, hips, knees and ankles. The other
1.2 body/blade/dorsal/digitigrade design remains in place. The comparison images
render the actual authored geometry and atlases at the same angle and scale.

Coordinates: feet at Y=0, front=-Z, anatomical left=+X. Bone transforms remain
neutral; rotations are local to cubes. All existing named rig controls and
animation data are unchanged. Knees remain at Y=13,Z=-1 and hocks at Y=4,Z=2;
`back_spines` still owns `back_blade_left/right`. The software inspection renderer
matches GeckoLib's mirrored X and source rotation convention, equivalent in
source coordinates to Rz(-Z) Ry(Y) Rx(-X). This avoids the old preview's inverted
sweep angles while leaving the runtime model coordinates unchanged.

The existing 37 animation clips are preserved byte-for-byte for 1.3.0. Do not
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

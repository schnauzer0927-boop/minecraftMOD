# Original combat resources

`generate_fx_assets.py` generates all seven RGBA particle sprite sets and twelve
original synthesized combat SE. These generated resources are distributed under
the repository's MIT license. The boss music is the user's supplied Suno composition
and is imported separately; the old synthetic music placeholder is never regenerated.

To regenerate with Python 3, numpy, Pillow and ffmpeg installed:

```sh
python3 scripts/assets/generate_fx_assets.py
python3 scripts/assets/generate_healing_assets.py
```

The signal and sprites use fixed random seeds. Encoder versions may produce different
Ogg container bytes while retaining the same sound. Combat SE are mono, 44.1 kHz, Vorbis.

Resource packs can replace `assets/apexpredator/sounds/<event>.ogg` and
`textures/particle/apex_<kind>.png` without Java changes. Event definitions and subtitle
keys are in `assets/apexpredator/sounds.json` and the language files.

Effects are server-to-client semantic cues. The client retains at most 72 effects,
spawns at most 160 particles in one tick, and submits at most 4096 effect quads per frame.
LOW/MEDIUM/HIGH particle density is 35%/65%/100% and also respects vanilla's particle
preference. Warning boundaries stay visible at every quality. Camera motion is a short
render-only angular offset; screen pulses tint edges and never change game speed.
Terrain debris is decorative unless the server explicitly enables its separate terrain
setting; these particle resources themselves never damage blocks.

`generate_healing_assets.py` adds the Phase 3 recovery resources without rewriting
the original sprites, model, armor texture or 13 sound files. It creates two quiet
mono Vorbis samples (`heal_entry`, `heal_hum`) and a 512×512 recovery glow mask
containing only 468 already-emissive texels. The chest receives the strongest
weight, neck and joint seams less; eyes, blades and dorsal plates are excluded.
The original generator now preserves additive `sounds.json` entries, including
both recovery sounds. Existing animation clips and attack effects remain unchanged.

Recovery effects follow the server-synchronized entity stage. The extra mask
flashes during the anticipation and pulses during healing; a few existing
`apex_core` / `apex_energy` particles move inward toward the current chest
position. Hit cancellation stops new particle generation and the extra glow.
All recovery particles share the existing quality controls and per-tick budget.

# Supplied boss music

The user supplied **The Stalking Motif**, created with Suno by `schnauzer0927`.
Its unmodified MP3 is retained at `audio/source/the_stalking_motif.mp3` for
reproducibility and remains outside `src/main/resources`, so it is not in the JAR.
See [audio/README.md](../../audio/README.md) for source provenance and measured edges.

To import with Python 3, numpy, ffmpeg with the `libvorbis` encoder, and ffprobe:

```sh
python3 scripts/assets/import_boss_music.py
```

Missing tools cause a clear failure; the conversion is not silently skipped.
The importer verifies the audited original MP3 hash, strips the embedded cover and
metadata, preserves stereo at 48 kHz and the original gain, and encodes Vorbis quality 6
to `assets/apexpredator/sounds/boss_music.ogg`. It trims only 0.320 seconds of leading
near-silent encoder residue and applies 5 ms / 10 ms anti-click fades. The complete
musical intro and fading outro are retained. The result is 174.480 seconds long,
with -4.79 dBFS measured peak and approximately -81 dBFS end-to-start sample step.
It repeats the complete song, including its natural fade and rising intro;
the music has not been rearranged into a beat-perfect loop.

`sounds.json` retains the event `apexpredator:boss_music` with `stream: true`.
The custom client sound instance loops the stream and uses the `RECORDS` category;
it never changes the audio gain when the stream loops. The reproducible conversion
audit is written to ignored `build/music/audio-audit.json` and intermediates stay
in ignored `build/music`, outside packaged resources. Running the combat or healing
asset generator preserves this imported music and its sound registration.

## Beacon and Fang assets (1.5.0)

Run `python3 scripts/assets/generate_progression_assets.py` to author the two
128×128 item atlases, vanilla 3D item models, shaped recipes, advancements and
additional English/Japanese translations. Python, Pillow and NumPy are authoring
dependencies; Minecraft requires none of them. The script preserves existing
language entries and does not write boss geometry, animations, loot, sounds or
combat resources.

Orange model accents use Forge 1.20.1 `forge_data` with block/sky light 15 and
ambient occlusion disabled. Armor keeps ordinary lighting; the item does not
illuminate world blocks. Item coordinates, allowed rotations and UV bounds are
checked before output. Offline model previews under ignored `build/progression`
describe the authored geometry and are separate from real client screenshots.

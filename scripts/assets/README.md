# Original combat resources

`generate_fx_assets.py` generates all seven RGBA particle sprite sets and thirteen
original synthesized Vorbis sounds, including a twelve-second looping industrial
encounter track. No third-party recordings or artwork are included. These generated
resources are distributed under the repository's MIT license.

To regenerate with Python 3, numpy, Pillow and ffmpeg installed:

```sh
python3 scripts/assets/generate_fx_assets.py
```

The signal and sprites use fixed random seeds. Encoder versions may produce different
Ogg container bytes while retaining the same sound. Audio is mono, 44.1 kHz, Vorbis.

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

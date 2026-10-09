# The Stalking Motif

The user-supplied Suno track was imported at their request for Apex Predator's
boss battles. Its original MP3 metadata identifies the title as `The Stalking
Motif`, the artist as `schnauzer0927`, and the lyrics field as `[Instrumental]`.
These are file metadata, not authorship inferred from the repository name.
The track is separate from the repository's generated MIT-licensed combat samples.

The original file is retained unchanged at
`source/the_stalking_motif.mp3` (4,167,378 bytes), outside the packaged main resources.
Its SHA-256 is
`875e429fcc2b8a28e55d7205e7f32d88e58e3f180a839d32ad0b0f542b54bc76`.
The MP3 contains stereo 48 kHz audio and embedded cover art. Container duration
is 174.840 seconds; after decoding MP3 padding, the music is 174.800 seconds.

[import_boss_music.py](../scripts/assets/import_boss_music.py) reproducibly encodes
it to `assets/apexpredator/sounds/boss_music.ogg` as stereo 48 kHz Ogg Vorbis quality 6.
Only the first 0.320 seconds of near-silent encoder residue are removed; the
original rising intro and fading outro are kept. A 5 ms beginning fade and 10 ms
ending fade reduce the discontinuity at the repeated stream boundary. No gain
normalization or compression is applied, and the embedded cover is omitted.

Measured with ffmpeg 7.1.5:

| Measurement | Source MP3 PCM | Imported OGG PCM |
| --- | ---: | ---: |
| Duration | 174.800 s | 174.480 s |
| Peak | -4.962 dBFS | -4.787 dBFS |
| RMS | -16.761 dBFS | -16.802 dBFS |
| End-to-start sample discontinuity | -42.956 dBFS | -81.116 dBFS |

Source integrated loudness is approximately -14.5 LUFS and true peak -5.0 dBFS.
The imported track has ample peak headroom; the game's master and `RECORDS` volume
controls apply on top of the custom instance's restrained default volume.
The natural outro and intro remain audible on each repeat. This is a full-song
repeat with click-safe edges, rather than a newly composed beat-perfect loop.

The import writes its full PCM audit to ignored `build/music/audio-audit.json`.
Ogg container identifiers can differ between encoder runs; PCM duration/layout,
edge recipe and audible content remain reproducible. Only the final OGG is
distributed in the mod JAR.

#!/usr/bin/env python3
"""Import the supplied Suno track as streamed, stereo Ogg Vorbis boss music.

Requires Python 3, numpy, ffmpeg with libvorbis, and ffprobe. The fixed trim is
specific to this source file; changing the composition requires a new edge audit.
The MP3 is retained outside main resources and is never bundled in the mod JAR.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import shutil
import subprocess
import tempfile

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE = ROOT / "audio/source/the_stalking_motif.mp3"
DEFAULT_OUTPUT = ROOT / "src/main/resources/assets/apexpredator/sounds/boss_music.ogg"
SOURCE_SHA256 = "875e429fcc2b8a28e55d7205e7f32d88e58e3f180a839d32ad0b0f542b54bc76"
LEADING_TRIM_SECONDS = 0.320
FADE_IN_SECONDS = 0.005
FADE_OUT_SECONDS = 0.010
VORBIS_QUALITY = 6


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def probe(path):
    completed = subprocess.run(
        ["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", str(path)],
        check=True, capture_output=True, text=True,
    )
    return json.loads(completed.stdout)


def decode(path, channels):
    completed = subprocess.run(
        ["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(path),
         "-map", "0:a:0", "-vn", "-c:a", "pcm_f32le", "-f", "f32le", "-"],
        check=True, capture_output=True,
    )
    return np.frombuffer(completed.stdout, dtype="<f4").reshape(-1, channels)


def db(value):
    return float(20 * math.log10(max(float(value), 1e-12)))


def pcm_summary(pcm, sample_rate):
    edge = max(1, round(sample_rate * 0.100))
    return {
        "sample_count": len(pcm),
        "decoded_duration_seconds": len(pcm) / sample_rate,
        "peak_dbfs": db(np.max(np.abs(pcm))),
        "rms_dbfs": db(np.sqrt(np.mean(pcm.astype(np.float64) ** 2))),
        "first_100ms_rms_dbfs": db(np.sqrt(np.mean(pcm[:edge].astype(np.float64) ** 2))),
        "last_100ms_rms_dbfs": db(np.sqrt(np.mean(pcm[-edge:].astype(np.float64) ** 2))),
        "first_sample": pcm[0].tolist(),
        "last_sample": pcm[-1].tolist(),
        "loop_boundary_step_dbfs": db(np.max(np.abs(pcm[0] - pcm[-1]))),
    }


def import_music(source, output, audit):
    for tool in ["ffmpeg", "ffprobe"]:
        if shutil.which(tool) is None:
            raise SystemExit(f"Required tool is missing: {tool}; install it before importing boss music.")
    if not source.is_file():
        raise SystemExit(f"Original supplied MP3 is missing: {source}")
    if sha256(source) != SOURCE_SHA256:
        raise SystemExit("MP3 differs from the audited Suno source; re-audit its edges before changing the trim recipe.")
    source_probe = probe(source)
    audio = next(s for s in source_probe["streams"] if s["codec_type"] == "audio")
    channels, sample_rate = int(audio["channels"]), int(audio["sample_rate"])
    source_pcm = decode(source, channels)
    source_summary = pcm_summary(source_pcm, sample_rate)
    duration = source_summary["decoded_duration_seconds"] - LEADING_TRIM_SECONDS
    if duration <= FADE_IN_SECONDS + FADE_OUT_SECONDS:
        raise SystemExit("Source is too short for the audited import recipe.")
    # The source already has ~5 dB of peak headroom. Preserve its dynamics and gain.
    filters = (
        f"atrim=start={LEADING_TRIM_SECONDS:.6f},asetpts=PTS-STARTPTS,"
        f"afade=t=in:st=0:d={FADE_IN_SECONDS:.6f},"
        f"afade=t=out:st={duration - FADE_OUT_SECONDS:.6f}:d={FADE_OUT_SECONDS:.6f}"
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    work = ROOT / "build/music"
    work.mkdir(parents=True, exist_ok=True)
    # Keep intermediate files outside main resources so concurrent resource scans
    # can only see the final validated OGG, never an incomplete conversion.
    with tempfile.TemporaryDirectory(prefix="apex-music-", dir=work) as temp:
        encoded = Path(temp) / "boss_music.ogg"
        subprocess.run(
            ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
             "-map", "0:a:0", "-vn", "-map_metadata", "-1", "-af", filters,
             "-c:a", "libvorbis", "-q:a", str(VORBIS_QUALITY), "-ar", str(sample_rate),
             "-ac", str(channels), str(encoded)], check=True,
        )
        encoded_probe = probe(encoded)
        streams = encoded_probe["streams"]
        if len(streams) != 1 or streams[0]["codec_name"] != "vorbis":
            raise SystemExit("Conversion did not produce a single Vorbis audio stream.")
        if int(streams[0]["channels"]) != channels or int(streams[0]["sample_rate"]) != sample_rate:
            raise SystemExit("Conversion unexpectedly changed the stereo/sample-rate layout.")
        encoded_pcm = decode(encoded, channels)
        encoded_summary = pcm_summary(encoded_pcm, sample_rate)
        if abs(encoded_summary["decoded_duration_seconds"] - duration) > 1 / sample_rate:
            raise SystemExit("Converted sample count differs from the expected trimmed duration.")
        if encoded_summary["peak_dbfs"] > -1:
            raise SystemExit("Vorbis output exceeds the required -1 dBFS headroom.")
        encoded.replace(output)
    report = {
        "title": "The Stalking Motif",
        "creator": "schnauzer0927 (user-supplied Suno composition)",
        "source": str(source.relative_to(ROOT)) if source.is_relative_to(ROOT) else str(source),
        "source_sha256": SOURCE_SHA256,
        "source_bytes": source.stat().st_size,
        "source_container_duration_seconds": float(source_probe["format"]["duration"]),
        "source_pcm": source_summary,
        "leading_trim_seconds": LEADING_TRIM_SECONDS,
        "trailing_trim_seconds": 0.0,
        "fade_in_seconds": FADE_IN_SECONDS,
        "fade_out_seconds": FADE_OUT_SECONDS,
        "gain_db": 0.0,
        "vorbis_quality": VORBIS_QUALITY,
        "sample_rate": sample_rate,
        "channels": channels,
        "output": str(output.relative_to(ROOT)) if output.is_relative_to(ROOT) else str(output),
        "output_sha256": sha256(output),
        "output_bytes": output.stat().st_size,
        "output_pcm": encoded_summary,
        "embedded_cover_removed": True,
        "loop_note": "Quiet encoder residue trimmed; original rising intro and fading outro retained. "
                     "Tiny edge fades avoid clicks; this is a full-song repeat, not a newly arranged beat-perfect loop.",
        "ffmpeg_version": subprocess.run(["ffmpeg", "-version"], check=True, capture_output=True,
                                          text=True).stdout.splitlines()[0],
    }
    audit.parent.mkdir(parents=True, exist_ok=True)
    audit.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(f"Imported {report['title']}: {duration:.3f} s, {channels} channels, "
          f"{sample_rate} Hz Vorbis; peak {encoded_summary['peak_dbfs']:.2f} dBFS.")
    print(f"Output: {output}\nAudit: {audit}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--audit", type=Path, default=ROOT / "build/music/audio-audit.json")
    args = parser.parse_args()
    import_music(args.source.resolve(), args.output.resolve(), args.audit.resolve())

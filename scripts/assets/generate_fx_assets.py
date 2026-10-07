#!/usr/bin/env python3
"""Regenerate original Apex sprites and synthetic sound samples (Python + numpy/Pillow + ffmpeg).

No downloaded images or third-party sound recordings are used. Samples are mono Vorbis,
44100 Hz; resource-pack authors may replace any identically named .ogg file.
"""
from pathlib import Path
import json
import math
import subprocess
import tempfile
import wave

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/apexpredator"
RATE = 44100
SOUND_NAMES = ["blade_swing", "blade_hit", "charge_start", "charge_dash", "slam", "shockwave", "teleport",
               "phase_change", "rage", "special_charge", "special_attack", "death", "boss_music"]
DURATION = [.28, .38, .70, .40, .70, .80, .55, 1.65, 1.9, 1.8, .95, 2.2, 12.0]


def tone(t, frequency, decay=0, phase=0):
    return np.sin(2 * np.pi * frequency * t + phase) * np.exp(-decay * t)


def lowpass(signal, width):
    return np.convolve(signal, np.ones(width) / width, mode="same")


def sound(name, duration, seed):
    t = np.arange(round(RATE * duration)) / RATE
    rng = np.random.default_rng(seed)
    noise = rng.normal(0, 1, len(t))
    low = lowpass(noise, 70)
    envelope = np.sin(np.pi * t / duration) ** .7
    if name == "blade_swing":
        s = noise * envelope * .26 + np.sin(2 * np.pi * (850 * t - 1000 * t * t)) * envelope * .2
    elif name == "blade_hit":
        s = .3 * noise * np.exp(-25 * t)
        for freq in [930, 1481, 2347, 3989]:
            s += .19 * tone(t, freq, 10 + freq / 800)
        s += .2 * tone(t, 88, 18)
    elif name == "charge_start":
        s = .45 * np.sin(2 * np.pi * (65 * t + 200 * t * t)) * envelope + .7 * low * envelope
        s += .15 * tone(t, 493) * envelope * (.55 + .45 * np.sin(45 * t))
    elif name == "charge_dash":
        s = .35 * noise * envelope + .6 * np.sin(2 * np.pi * (180 * t - 120 * t * t)) * envelope
    elif name == "slam":
        s = .62 * tone(t, 49, 5) + .3 * tone(t, 83, 9) + .4 * noise * np.exp(-17 * t) + low * np.exp(-4 * t)
    elif name == "shockwave":
        s = .62 * np.sin(2 * np.pi * (115 * t - 65 * t * t)) * np.exp(-4 * t) + 1.3 * low * envelope + .12 * noise * np.exp(-13 * t)
    elif name == "teleport":
        s = .3 * np.sin(2 * np.pi * (680 * t + 1000 * t * t)) * envelope + .18 * noise * envelope
        s += .13 * tone(t, 1119) * np.sin(24 * t) * envelope
    elif name == "phase_change":
        s = .4 * tone(t, 73) * envelope + .26 * tone(t, 110) * envelope + .19 * tone(t, 147) * envelope
        s += .4 * low * envelope + .18 * np.sin(2 * np.pi * (180 * t + 120 * t * t)) * envelope
        s += .15 * noise * np.exp(-14 * t)
    elif name == "rage":
        s = (.52 * tone(t, 52) + .25 * tone(t, 77) + .16 * tone(t, 104)) * envelope * (.7 + .3 * np.sin(34 * t))
        s += 1.4 * low * envelope + .12 * np.sin(2 * np.pi * (230 * t - 34 * t * t)) * envelope
    elif name == "special_charge":
        rising = (t / duration) ** .7
        s = (.3 * tone(t, 73) + .19 * tone(t, 146) + .13 * tone(t, 219) + .08 * tone(t, 292)) * rising
        s += .2 * np.sin(2 * np.pi * (320 * t + 500 * t * t)) * rising * (.6 + .4 * np.sin(60 * t))
        s += noise * rising * .13
    elif name == "special_attack":
        s = .48 * tone(t, 43, 4) + .28 * tone(t, 86, 5) + .35 * noise * np.exp(-8 * t)
        s += .2 * np.sin(2 * np.pi * (1800 * t - 1500 * t * t)) * np.exp(-7 * t)
        s += low * envelope
    elif name == "death":
        s = .46 * np.sin(2 * np.pi * (92 * t - 16 * t * t)) * np.exp(-1.5 * t)
        s += .24 * np.sin(2 * np.pi * (287 * t - 30 * t * t)) * np.exp(-2 * t)
        s += low * np.exp(-2 * t) + .12 * noise * np.exp(-9 * t)
    else:
        # Sixteen beats at 80 BPM: a seamless original restrained industrial encounter bed.
        beat = .75
        phase = t % beat
        bars = (t / (beat * 4)).astype(int)
        bass = np.array([73.416, 65.406, 58.270, 65.406])[bars % 4]
        s = (.2 * np.sin(2 * np.pi * bass * t) + .08 * np.sin(2 * np.pi * bass * 2 * t)) * (.55 + .45 * np.exp(-7 * phase))
        s += .23 * np.sin(2 * np.pi * (52 * phase + 1.4 * np.exp(-30 * phase))) * np.exp(-22 * phase)
        snares = (np.floor(t / beat).astype(int) % 2 == 1).astype(float)
        s += .1 * noise * np.exp(-35 * phase) * snares
        s += .055 * noise * np.exp(-90 * (t % (beat / 2)))
        for note in [146.832, 174.614, 220.0]:
            s += .025 * tone(t, note) * (.7 + .3 * np.sin(2 * np.pi * t / 12))
    # Tiny zeroed edges avoid clicks, including when the music loop restarts.
    fade = np.minimum(1, t / .008) * np.minimum(1, (duration - t) / .025)
    s *= fade
    return s / max(1, np.max(np.abs(s)) / .83)


def generate_audio():
    folder = ASSETS / "sounds"
    folder.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="apex-sounds-") as temp:
        for index, (name, duration) in enumerate(zip(SOUND_NAMES, DURATION)):
            samples = sound(name, duration, 4700 + index)
            source = Path(temp) / f"{name}.wav"
            with wave.open(str(source), "wb") as wav:
                wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(RATE)
                wav.writeframes((samples * 32767).astype("<i2").tobytes())
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
                            "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "5", str(folder / f"{name}.ogg")], check=True)
    entries = {name: {"subtitle": f"sound.apexpredator.{name}", "sounds": [{"name": f"apexpredator:{name}", "stream": name == "boss_music"}]}
               for name in SOUND_NAMES}
    (ASSETS / "sounds.json").write_text(json.dumps(entries, indent=2) + "\n")


def generate_sprites():
    folder = ASSETS / "textures/particle"
    specs = ASSETS / "particles"
    folder.mkdir(parents=True, exist_ok=True); specs.mkdir(parents=True, exist_ok=True)
    size = 32
    yy, xx = np.mgrid[0:size, 0:size]
    x = (xx + .5 - size / 2) / (size / 2); y = (yy + .5 - size / 2) / (size / 2)
    radius = np.sqrt(x*x + y*y)
    rng = np.random.default_rng(120147)
    for style in ["slash", "energy", "smoke", "spark", "afterimage", "shockwave", "core"]:
        if style == "smoke":
            alpha = np.maximum(0, 1 - radius) * (.45 + .55 * rng.random((size, size)))
        elif style == "spark":
            alpha = np.exp(-(x*x / .018 + y*y / .36))
        elif style == "slash":
            alpha = np.exp(-((radius - .62) ** 2) / .008) * (x > -.35) * (.7 + .3 * np.maximum(0, y))
        elif style == "shockwave":
            alpha = np.exp(-((radius - .70) ** 2) / .007)
        elif style == "afterimage":
            alpha = np.exp(-(x*x / .11 + y*y / .45)) * .65
        elif style == "core":
            alpha = np.exp(-(radius ** 2) / .08) + .18 * np.exp(-(radius ** 2) / .36)
        else:
            alpha = np.maximum(0, 1 - radius) ** 2
        rgba = np.zeros((size, size, 4), dtype=np.uint8)
        rgba[:, :, :3] = 255
        rgba[:, :, 3] = np.clip(alpha * 255, 0, 255).astype(np.uint8)
        image = Image.fromarray(rgba)
        if style == "smoke": image = image.filter(ImageFilter.GaussianBlur(1.1))
        image.save(folder / f"apex_{style}.png")
        (specs / f"apex_{style}.json").write_text(json.dumps({"textures": [f"apexpredator:apex_{style}"]}, indent=2) + "\n")


def language():
    english = ["Apex blade sweeps", "Apex blade strikes", "Apex coils to charge", "Apex rushes", "Apex slams down", "Apex releases a shockwave",
               "Apex phases through space", "Apex armor unfolds", "Apex roars", "Apex Execution charges", "Apex Execution strikes", "Apex core collapses", "Apex encounter music"]
    japanese = ["Apexの刃が空を裂く", "Apexの刃が命中", "Apexが突進を構える", "Apexが突進", "Apexが地面へ叩きつける", "Apexの衝撃波",
                "Apexが転移", "Apexの装甲が展開", "Apexの咆哮", "APEX EXECUTIONの予兆", "APEX EXECUTION発動", "Apexのコアが崩壊", "Apex戦闘曲"]
    for locale, translations in [("en_us", english), ("ja_jp", japanese)]:
        target = ASSETS / "lang" / f"{locale}.json"
        data = json.loads(target.read_text())
        data.update({f"sound.apexpredator.{name}": translation for name, translation in zip(SOUND_NAMES, translations)})
        data["effect.apexpredator.execution"] = "APEX EXECUTION"
        target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    generate_sprites(); generate_audio(); language()
    print("Generated 7 particle sprite sets and 13 original sound samples.")

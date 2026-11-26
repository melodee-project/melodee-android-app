#!/usr/bin/env python3
"""Rebuild Melodee's six original 32-second ambient preview fixtures.

Optional authoring tool only: requires Python 3, numpy and ffmpeg. Android builds
use the committed Ogg files and do not execute this script. No source recordings,
third-party music, network downloads or server audio are used.
"""
from pathlib import Path
import subprocess
import tempfile
import wave

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "app/src/main/assets/audio"
SAMPLE_RATE = 22050
DURATION = 32


def frequency(midi):
    return 440.0 * 2.0 ** ((midi - 69) / 12.0)


def render(tonic, variant):
    count = SAMPLE_RATE * DURATION
    timeline = np.arange(count) / SAMPLE_RATE
    output = np.zeros(count)
    progression = [(0, 3, 7, 10), (5, 8, 12, 15), (8, 12, 15, 19), (3, 7, 10, 14)]
    for section, chord in enumerate(progression):
        start, end = section * 8 * SAMPLE_RATE, (section + 1) * 8 * SAMPLE_RATE
        time = timeline[start:end] - section * 8
        envelope = np.minimum(time / 0.6, 1) * np.minimum((8 - time) / 0.9, 1)
        for interval in chord:
            hz = frequency(tonic + interval)
            pad = np.sin(2 * np.pi * hz * time) + 0.22 * np.sin(2 * np.pi * hz * 2 * time)
            output[start:end] += 0.035 * pad * envelope
        for note in range(8):
            offset = start + note * SAMPLE_RATE
            length = min(2 * SAMPLE_RATE, count - offset)
            local = np.arange(length) / SAMPLE_RATE
            hz = frequency(tonic + 12 + chord[(note + variant) % len(chord)])
            attack = np.minimum(local / 0.012, 1)
            tone = np.sin(2 * np.pi * hz * local) + 0.18 * np.sin(2 * np.pi * 3 * hz * local)
            output[offset:offset + length] += 0.07 * tone * attack * np.exp(-local * 2.8)
    fade = np.minimum(timeline / 0.08, 1) * np.minimum((DURATION - timeline) / 0.15, 1)
    return (np.clip(output * fade, -0.8, 0.8) * 32767).astype('<i2')


def main():
    DESTINATION.mkdir(parents=True, exist_ok=True)
    fixtures = [("first-light", 57), ("open-road", 60), ("afterglow", 53),
                ("blue-hour", 50), ("slow-motion", 55), ("homeward", 58)]
    with tempfile.TemporaryDirectory() as directory:
        for variant, (name, tonic) in enumerate(fixtures):
            source = Path(directory) / f"{name}.wav"
            with wave.open(str(source), 'wb') as stream:
                stream.setnchannels(1)
                stream.setsampwidth(2)
                stream.setframerate(SAMPLE_RATE)
                stream.writeframes(render(tonic, variant).tobytes())
            destination = DESTINATION / f"{name}.ogg"
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(source),
                            '-c:a', 'libvorbis', '-q:a', '4', '-map_metadata', '-1',
                            str(destination)], check=True)
            print(f"Created {destination.name}: {destination.stat().st_size} bytes")


if __name__ == '__main__':
    main()

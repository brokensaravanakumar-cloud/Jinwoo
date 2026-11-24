"""Generates the original JIN WOO activation sound (no samples, no copyrighted material).
Layers: deep sub-bass hit, rising noise + sweep, electric zaps, final impact, shimmer tail with a simple echo.
Run: python generate_activation.py [output.wav]"""
import sys, wave
import numpy as np

SR = 22050
DUR = 3.2
t = np.linspace(0, DUR, int(SR * DUR), endpoint=False)
rng = np.random.default_rng(7)
out = np.zeros_like(t)

def env(start, attack, decay, length=None):
    e = np.zeros_like(t)
    s = int(start * SR)
    n = len(t) - s if length is None else min(int(length * SR), len(t) - s)
    tt = np.arange(n) / SR
    a = np.clip(tt / max(attack, 1e-4), 0, 1)
    e[s:s + n] = a * np.exp(-tt / decay)
    return e

# 1) opening sub-bass drone sweeping down
f = 95 * np.exp(-t * 0.55) + 32
out += 0.55 * np.sin(2 * np.pi * np.cumsum(f) / SR) * env(0.0, 0.05, 1.1)

# 2) riser: noise (brightened) + rising sine sweep, 0.2s -> 1.3s
mask = (t > 0.2) & (t < 1.3)
rise = np.clip((t - 0.2) / 1.1, 0, 1) ** 2 * mask
noise = np.diff(rng.standard_normal(len(t) + 1))
out += 0.30 * noise * rise
sweep_f = 180 + 2200 * np.clip((t - 0.2) / 1.1, 0, 1) ** 2
out += 0.18 * np.sin(2 * np.pi * np.cumsum(sweep_f) / SR) * rise

# 3) electric zaps (FM bursts) between 0.5s and 1.3s
for z in (0.55, 0.78, 0.95, 1.08, 1.18):
    e = env(z, 0.002, 0.045, 0.18)
    fm = np.sin(2 * np.pi * (900 + 400 * rng.random()) * t + 6 * np.sin(2 * np.pi * 63 * t))
    out += 0.22 * fm * e

# 4) main impact at 1.3s: boom + noise burst
boom_f = 120 * np.exp(-(t - 1.3).clip(0) * 3.0) + 36
out += 0.85 * np.sin(2 * np.pi * np.cumsum(boom_f) / SR) * env(1.3, 0.004, 0.9)
out += 0.35 * rng.standard_normal(len(t)) * env(1.3, 0.001, 0.12)

# 5) shimmer tail: detuned fifths with tremolo
trem = 0.6 + 0.4 * np.sin(2 * np.pi * 6 * t)
for fr, g in ((440, 0.07), (660.5, 0.06), (880, 0.05), (1318, 0.035)):
    out += g * np.sin(2 * np.pi * fr * t) * trem * env(1.35, 0.25, 0.9)

# 6) cheap echo / space
for d, g in ((0.18, 0.35), (0.37, 0.22), (0.61, 0.12)):
    k = int(d * SR)
    out[k:] += g * out[:-k]

# fade out, soft clip, normalise
out *= np.clip((DUR - t) / 0.4, 0, 1)
out = np.tanh(out * 1.2)
out = out / np.max(np.abs(out)) * 0.89
pcm = (out * 32767).astype("<i2")

path = sys.argv[1] if len(sys.argv) > 1 else "activation.wav"
with wave.open(path, "wb") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print("wrote", path, f"{len(pcm)/SR:.2f}s")

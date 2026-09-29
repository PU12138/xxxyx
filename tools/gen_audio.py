#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
仙途·传奇 —— 程序化生成背景音乐与音效（WAV）。
仅使用 Python 标准库（wave, math, struct, random），不依赖 numpy。
输出到 res/raw 目录，供 Android MediaPlayer / SoundPool 加载。
"""
import wave, math, struct, random, os

SR = 22050  # 采样率
OUT = "/workspace/app/src/main/res/raw"
random.seed(20260929)

# 五声音阶（宫商角徵羽），不同调式用于主旋律与和声
# 频率基于 A4=440，使用 just intonation 近似
PENTATONIC = {
    "C4": 261.63, "D4": 293.66, "E4": 329.63, "G4": 392.00, "A4": 440.00,
    "C5": 523.25, "D5": 587.33, "E5": 659.25, "G5": 783.99, "A5": 880.00,
    "C6": 1046.50, "E6": 1318.51, "G6": 1567.98,
}
BASS = {"C2": 65.41, "G2": 98.00, "A2": 110.00, "D3": 146.83, "C3": 130.81}


def write_wav(path, samples):
    """samples: list of float in [-1,1]"""
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for s in samples:
            v = max(-1.0, min(1.0, s))
            frames += struct.pack("<h", int(v * 32767))
        w.writeframes(bytes(frames))
    print("wrote", path, "%.1fs" % (len(samples) / SR))


def adsr(n, attack=0.01, decay=0.1, sustain=0.7, release=0.2, sr=SR):
    """返回长度 n 的包络列表"""
    env = [0.0] * n
    a = int(attack * sr)
    d = int(decay * sr)
    r = int(release * sr)
    sus_level = sustain
    for i in range(n):
        if i < a:
            env[i] = i / max(1, a)
        elif i < a + d:
            t = (i - a) / max(1, d)
            env[i] = 1.0 - (1.0 - sus_level) * t
        elif i < n - r:
            env[i] = sus_level
        else:
            t = (n - 1 - i) / max(1, r)
            env[i] = sus_level * t
    return env


def osc(freq, n, kind="sine"):
    out = [0.0] * n
    if kind == "sine":
        for i in range(n):
            out[i] = math.sin(2 * math.pi * freq * i / SR)
    elif kind == "triangle":
        for i in range(n):
            p = (freq * i / SR) % 1.0
            out[i] = (2 * abs(2 * p - 1) - 1)
    elif kind == "saw":
        for i in range(n):
            p = (freq * i / SR) % 1.0
            out[i] = 2 * p - 1
    elif kind == "square":
        for i in range(n):
            p = (freq * i / SR) % 1.0
            out[i] = 1.0 if p < 0.5 else -1.0
    return out


def note(freq, dur, kind="sine", attack=0.02, release=0.3, vol=0.6):
    n = int(dur * SR)
    if freq <= 0:
        return [0.0] * n
    env = adsr(n, attack=attack, decay=0.05, sustain=0.8, release=release)
    o = osc(freq, n, kind)
    return [o[i] * env[i] * vol for i in range(n)]


def mix(*tracks):
    n = max(len(t) for t in tracks)
    out = [0.0] * n
    for t in tracks:
        for i, s in enumerate(t):
            out[i] += s
    # 软限幅
    return [math.tanh(s * 1.0) * 0.9 for s in out]


def concat(*tracks):
    out = []
    for t in tracks:
        out.extend(t)
    return out


def silence(dur):
    return [0.0] * int(dur * SR)


def gen_bgm():
    """生成约 30 秒的循环背景音乐：低音 pad + 五声主旋律 + 高音点缀"""
    # 和声进行（每小节 4 拍，bpm=66 -> 每拍 ~0.909s）
    beat = 60.0 / 66
    bar = beat * 4

    # 主旋律音符序列（节拍数, 音名）
    melody = [
        (2, "C5"), (1, "E5"), (1, "G5"), (2, "A5"), (2, "G5"),
        (2, "E5"), (2, "D5"), (2, "C5"), (2, "G4"),
        (2, "A4"), (2, "C5"), (2, "D5"), (2, "E5"),
        (4, "G5"), (2, "E5"), (2, "C5"),
        (4, "D5"), (4, "C5"),
    ]
    bass = [
        (4, "C2"), (4, "G2"), (4, "A2"), (4, "C2"),
        (4, "G2"), (4, "A2"), (4, "D3"), (4, "C3"),
    ]

    main = []
    for beats, name in melody:
        dur = beats * beat
        main.extend(note(PENTATONIC[name], dur, "triangle", attack=0.04, release=0.4, vol=0.5))
    pad = []
    for beats, name in bass:
        dur = beats * beat
        pad.extend(note(BASS[name], dur, "sine", attack=0.3, release=0.5, vol=0.45))

    # 高音点缀（随机零星点缀）
    sparkle = silence(len(main) / SR + 0.1)
    for _ in range(8):
        pos = random.randint(0, len(sparkle) - SR)
        nm = random.choice(["C6", "E6", "G6"])
        sp = note(PENTATONIC[nm], 0.5, "sine", attack=0.01, release=0.4, vol=0.18)
        for i, s in enumerate(sp):
            if pos + i < len(sparkle):
                sparkle[pos + i] += s

    track = mix(main, pad, sparkle)
    # 尾部淡出再淡入，便于无缝循环
    fade = int(1.2 * SR)
    for i in range(fade):
        a = i / fade
        track[i] *= a
        track[-(i + 1)] *= a
    # 循环衔接：把尾部衰减后的开头重新加一点
    return track


def gen_swing():
    """剑气挥砍：短促的扫频噪声"""
    n = int(0.18 * SR)
    out = [0.0] * n
    for i in range(n):
        t = i / SR
        freq = 2200 - 1800 * (t / 0.18)
        p = freq * i / SR
        s = math.sin(2 * math.pi * p)
        env = math.exp(-t * 18)
        out[i] = s * env * 0.5
    return out


def gen_hit():
    """命中：低频砰 + 噪声"""
    n = int(0.15 * SR)
    out = [0.0] * n
    for i in range(n):
        t = i / SR
        env = math.exp(-t * 22)
        low = math.sin(2 * math.pi * 120 * i / SR)
        noise = (random.random() * 2 - 1) * 0.5
        out[i] = (low * 0.7 + noise * 0.5) * env * 0.6
    return out


def gen_death():
    """怪物死亡：下行音"""
    parts = [
        note(440, 0.12, "saw", attack=0.005, release=0.1, vol=0.4),
        note(330, 0.12, "saw", attack=0.005, release=0.1, vol=0.4),
        note(220, 0.2, "sine", attack=0.005, release=0.15, vol=0.5),
    ]
    return concat(*parts)


def gen_break():
    """境界突破：明亮上行琶音"""
    parts = [note(PENTATONIC[n], 0.18, "triangle", attack=0.01, release=0.2, vol=0.6)
             for n in ["C5", "E5", "G5", "C6", "E6"]]
    # 叠加成和弦尾音
    chord = mix(
        note(PENTATONIC["C5"], 0.8, "sine", attack=0.05, release=0.6, vol=0.3),
        note(PENTATONIC["E5"], 0.8, "sine", attack=0.05, release=0.6, vol=0.3),
        note(PENTATONIC["G5"], 0.8, "sine", attack=0.05, release=0.6, vol=0.3),
    )
    return concat(concat(*parts), chord)


def gen_pickup():
    """拾取：清脆叮"""
    return concat(
        note(PENTATONIC["C6"], 0.08, "sine", attack=0.005, release=0.05, vol=0.5),
        note(PENTATONIC["G6"], 0.12, "sine", attack=0.005, release=0.08, vol=0.5),
    )


def gen_hurt():
    """玩家受击：闷响"""
    n = int(0.2 * SR)
    out = [0.0] * n
    for i in range(n):
        t = i / SR
        env = math.exp(-t * 10)
        low = math.sin(2 * math.pi * 90 * i / SR)
        noise = (random.random() * 2 - 1) * 0.4
        out[i] = (low * 0.8 + noise * 0.4) * env * 0.7
    return out


def gen_skill():
    """施法：升频嗡鸣"""
    n = int(0.35 * SR)
    out = [0.0] * n
    for i in range(n):
        t = i / SR
        freq = 300 + 1400 * (t / 0.35)
        p = freq * i / SR
        s = math.sin(2 * math.pi * p) + 0.3 * math.sin(2 * math.pi * p * 2)
        env = math.exp(-t * 6) * min(1.0, t * 40)
        out[i] = s * env * 0.4
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    write_wav(os.path.join(OUT, "bgm.wav"), gen_bgm())
    write_wav(os.path.join(OUT, "sfx_swing.wav"), gen_swing())
    write_wav(os.path.join(OUT, "sfx_hit.wav"), gen_hit())
    write_wav(os.path.join(OUT, "sfx_death.wav"), gen_death())
    write_wav(os.path.join(OUT, "sfx_break.wav"), gen_break())
    write_wav(os.path.join(OUT, "sfx_pickup.wav"), gen_pickup())
    write_wav(os.path.join(OUT, "sfx_hurt.wav"), gen_hurt())
    write_wav(os.path.join(OUT, "sfx_skill.wav"), gen_skill())
    print("ALL DONE")


if __name__ == "__main__":
    main()

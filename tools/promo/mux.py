"""يدمج أصوات المشاهد مع الفيديو الصامت: ضبط الطول بـ atempo، تأخير عند بداية كل مشهد، وخفض الموسيقى أثناء الكلام."""
import json, os, subprocess, sys, wave

HERE = os.path.dirname(os.path.abspath(__file__))
VOICE_DIR, SILENT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
sc = json.load(open(os.path.join(HERE, "script.json"), encoding="utf-8"))
inp = ["-i", SILENT]; fl = []; mix = []
for i, s in enumerate(sc):
    p = f"{VOICE_DIR}/{s['id']}.wav"
    with wave.open(p) as w: d = w.getnframes() / w.getframerate()
    room = s["dur"] - 0.5
    t = max(1.0, d / room) if d > room else 1.0
    t = min(t, 1.25)
    inp += ["-i", p]
    ms = int((s["start"] + 0.2) * 1000)
    fl.append(f"[{i+1}:a]atempo={t:.3f},aresample=44100,adelay={ms}|{ms},volume=1.6[v{i}]")
    mix.append(f"[v{i}]")
fl.append("".join(mix) + f"amix=inputs={len(sc)}:normalize=0,asplit=2[vo][sc]")
fl.append("[0:a][sc]sidechaincompress=threshold=0.02:ratio=8:attack=20:release=400[bed]")
fl.append("[bed][vo]amix=inputs=2:normalize=0,alimiter=limit=0.95[a]")
subprocess.check_call(["ffmpeg", "-y", "-loglevel", "error", *inp, "-filter_complex", ";".join(fl),
    "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", OUT])
print("ok", OUT)

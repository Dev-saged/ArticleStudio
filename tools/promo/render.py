"""يصيّر مشاهد scene.html إلى إطارات JPEG ثم يجمعها بـ ffmpeg في promo-silent.mp4 مع طبقة صوت موسيقية."""
import json, os, subprocess, sys, shutil
from concurrent.futures import ThreadPoolExecutor
from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
FPS, DUR = 30, 60
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "docs", "media", "promo-silent.mp4")
FR = "/tmp/promo-frames"

def html():
    s = open(os.path.join(HERE, "scene.html"), encoding="utf-8").read()
    sc = json.load(open(os.path.join(HERE, "script.json"), encoding="utf-8"))
    p = os.path.join(HERE, "_scene.html")
    open(p, "w", encoding="utf-8").write(s.replace("__SCRIPT__", json.dumps(sc, ensure_ascii=False)))
    return p

def work(args):
    page_path, frames = args
    with sync_playwright() as pw:
        b = pw.chromium.launch(executable_path="/usr/bin/google-chrome", args=["--no-sandbox", "--disable-gpu"])
        pg = b.new_page(viewport={"width": 1080, "height": 1920})
        pg.goto("file://" + page_path); pg.evaluate("document.fonts.ready")
        for i in frames:
            pg.evaluate(f"render({i / FPS})")
            pg.screenshot(path=f"{FR}/{i:05d}.jpg", type="jpeg", quality=90)
        b.close()

if __name__ == "__main__":
    only = [int(x) for x in os.environ.get("FRAMES", "").split(",") if x]
    shutil.rmtree(FR, ignore_errors=True); os.makedirs(FR)
    p = html(); idx = only or list(range(FPS * DUR))
    n = 2
    with ThreadPoolExecutor(n) as ex: list(ex.map(work, [(p, idx[k::n]) for k in range(n)]))
    if not only:
        bed = "sin(2*PI*55*t)*0.5+sin(2*PI*82.4*t)*0.35+sin(2*PI*110*t)*0.25+sin(2*PI*164.8*t)*(0.12+0.1*sin(2*PI*0.2*t))+sin(2*PI*220*t)*0.06*sin(2*PI*0.125*t)"
        subprocess.check_call(["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", f"{FR}/%05d.jpg",
            "-f", "lavfi", "-i", f"aevalsrc='{bed}':s=44100:d={DUR}",
            "-af", f"lowpass=f=900,volume=0.5,afade=t=in:d=2,afade=t=out:st={DUR-3}:d=3",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "21", "-preset", "veryfast", "-c:a", "aac", "-b:a", "160k", "-shortest", OUT])
        os.remove(p); print("ok", OUT)

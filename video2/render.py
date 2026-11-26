"""Renders demo.html frame by frame (30 fps, 1920x1080) with the installed Chrome and pipes JPEG frames to ffmpeg.
usage: py render.py [start] [end] [out.mp4]   or   py render.py --stills t1 t2 ...  (PNG test frames)"""
import asyncio, pathlib, subprocess, sys
from playwright.async_api import async_playwright

HERE = pathlib.Path(__file__).parent
FPS = 30

async def main():
    args = sys.argv[1:]
    async with async_playwright() as pw:
        b = await pw.chromium.launch(channel="chrome", args=["--autoplay-policy=no-user-gesture-required", "--allow-file-access-from-files"])
        pg = await b.new_page(viewport={"width": 1920, "height": 1080}, device_scale_factor=1)
        await pg.goto((HERE / "demo.html").as_uri())
        await pg.evaluate("window.ready()")
        if args and args[0] == "--stills":
            (HERE / "stills").mkdir(exist_ok=True)
            for t in args[1:]:
                await pg.evaluate(f"window.render({float(t)})")
                await pg.screenshot(path=str(HERE / "stills" / f"t{float(t):07.2f}.png"))
            await b.close(); return
        start = float(args[0]) if args else 0.0
        end = float(args[1]) if len(args) > 1 else 180.0
        out = args[2] if len(args) > 2 else str(HERE / "out" / "video.mp4")
        pathlib.Path(out).parent.mkdir(exist_ok=True)
        ff = subprocess.Popen(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "image2pipe", "-framerate", str(FPS), "-c:v", "mjpeg", "-i", "-",
                               "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-pix_fmt", "yuv420p", "-r", str(FPS), out], stdin=subprocess.PIPE)
        n0, n1 = round(start * FPS), round(end * FPS)
        for n in range(n0, n1):
            await pg.evaluate(f"window.render({n / FPS})")
            ff.stdin.write(await pg.screenshot(type="jpeg", quality=93))
            if n % 300 == 0: print(f"frame {n} / {n1}", flush=True)
        ff.stdin.close(); ff.wait()
        await b.close()
        print("done", out)

asyncio.run(main())

"""Edits the Conuco demo video from the real screen recordings (build/raw) and the voice-over (build/voice).
Each scene: raw segments (some fast-forwarded, with an honest "real time" badge) on a branded 1920x1080 frame,
a caption bar on top, narration, then all scenes are joined and English subtitles are burned in."""
import json
import pathlib
import subprocess

HERE = pathlib.Path(__file__).parent
B = HERE / "build"
RAW, CLIPS = B / "raw", B / "clips"
CLIPS.mkdir(parents=True, exist_ok=True)
PHONE = pathlib.Path(r"C:\Users\admin\AppData\Local\Temp\claude\58bb8fca1af19e42\ad54195d-168e-43dd-a7aa-fdf4f5cbff33\scratchpad")
FONT = "C\\:/Windows/Fonts/segoeuib.ttf"
FONT_R = "C\\:/Windows/Fonts/segoeui.ttf"
BG = "0x0d0e11"
YELLOW = "0xF5C518"
GREEN = "0x4FB477"
W, H, TOP = 1920, 1080, 92          # canvas and caption bar height
VW, VH = 1842, 880                    # recorded page area scaled into the canvas
FPS = 30
timing = {s["id"]: s for s in json.loads((B / "timing.json").read_text(encoding="utf-8"))}


def run(args):
    r = subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args], capture_output=True, text=True)
    if r.returncode:
        raise SystemExit(r.stderr[-2000:])


def dur(p):
    return float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(p)],
                                capture_output=True, text=True).stdout)


def esc(t):
    return t.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\u2019").replace("%", "\\%")


def chrome(caption):
    """Caption bar: step title left (yellow), project tag right."""
    return (f"drawtext=fontfile='{FONT}':text='{esc(caption)}':fontsize=34:fontcolor={YELLOW}:x=48:y=26,"
            f"drawtext=fontfile='{FONT_R}':text='Conuco  ·  #BuildHybridAI  ·  real recording':fontsize=24:fontcolor=0xa2a8b6:x=w-tw-48:y=33")


def badge(text):
    return (f"drawbox=x=(iw-1440)/2:y=(ih-110)/2:w=1440:h=110:color=0x000000@0.72:t=fill,"
            f"drawtext=fontfile='{FONT}':text='{esc(text)}':fontsize=34:fontcolor={YELLOW}:x=(w-tw)/2:y=(h-th)/2")


def segment(out, raw, a, b, caption, speed=1.0, note=None):
    vf = f"setpts=(PTS-STARTPTS)/{speed},scale={VW}:{VH},pad={W}:{H}:{(W - VW) // 2}:{TOP}:color={BG},{chrome(caption)}"
    if note:
        vf += "," + badge(note)
    run(["-ss", str(a), "-to", str(b), "-i", str(RAW / f"{raw}.mp4"), "-vf", vf, "-r", str(FPS), "-an",
         "-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", str(out)])


def still_pair(out, left, right, caption, seconds):
    """Two real phone screenshots side by side."""
    run(["-loop", "1", "-t", str(seconds), "-i", str(PHONE / left), "-loop", "1", "-t", str(seconds), "-i", str(PHONE / right),
         "-filter_complex",
         f"[0]scale=-2:860[a];[1]scale=-2:860[b];color=c={BG}:s={W}x{H}:d={seconds}[bg];"
         f"[bg][a]overlay=x=W/2-w-40:y={TOP}+20[t];[t][b]overlay=x=W/2+40:y={TOP}+20,{chrome(caption)},"
         f"drawtext=fontfile='{FONT_R}':text='Galaxy S24 Ultra · real screenshots of the Kaye Android app':fontsize=24:fontcolor=0xa2a8b6:x=(w-tw)/2:y=h-40",
         "-r", str(FPS), "-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", str(out)])


def phone_seg(out, raw, a, b, caption, speed=1.0, note=None):
    """Portrait phone screen recording, centred on the frame."""
    vf = (f"setpts=(PTS-STARTPTS)/{speed},scale=-2:900,pad={W}:{H}:(ow-iw)/2:{TOP}+8:color={BG},{chrome(caption)},"
          f"drawtext=fontfile='{FONT}':text='Galaxy S24 Ultra':fontsize=30:fontcolor=white:x=80:y={TOP}+60,"
          f"drawtext=fontfile='{FONT_R}':text='Conuco Android app':fontsize=26:fontcolor=0xa2a8b6:x=80:y={TOP}+104,"
          f"drawtext=fontfile='{FONT_R}':text='real screen recording':fontsize=26:fontcolor=0xa2a8b6:x=80:y={TOP}+140")
    if note:
        vf += "," + badge(note)
    run(["-ss", str(a), "-to", str(b), "-i", str(RAW / f"{raw}.mp4"), "-vf", vf, "-r", str(FPS), "-an",
         "-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", str(out)])


def end_card(out, seconds=4.0):
    lines = [("Conuco", 120, "white", 330), ("The field assistant that keeps working where the signal stops.", 44, YELLOW, 480),
             ("petiht.com/conuco   ·   Web (Chrome) + Android", 34, "0xd7dbe4", 590),
             ("Firebase AI Logic · Gemini Nano · App Check · Firestore · Auth · Remote Config · Analytics", 26, "0xa2a8b6", 680),
             ("#BuildHybridAI 2026", 28, "0xa2a8b6", 740)]
    vf = ",".join(f"drawtext=fontfile='{FONT}':text='{esc(t)}':fontsize={s}:fontcolor={c}:x=(w-tw)/2:y={y}" for t, s, c, y in lines)
    run(["-f", "lavfi", "-i", f"color=c={BG}:s={W}x{H}:d={seconds}", "-vf", vf + ",drawbox=x=(iw-420)/2:y=455:w=420:h=4:color=0x4FB477:t=fill",
         "-r", str(FPS), "-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", str(out)])


def concat_video(parts, out):
    lst = out.with_suffix(".txt")
    lst.write_text("".join(f"file '{p.resolve().as_posix()}'\n" for p in parts), encoding="utf-8")
    run(["-f", "concat", "-safe", "0", "-i", str(lst), "-c", "copy", str(out)])


def scene(sid, parts, lead=0.35, tail=0.5):
    """Joins a scene's video parts, holds the last frame if the narration is longer, adds the voice."""
    silent = CLIPS / f"{sid}_v.mp4"
    concat_video(parts, silent)
    vlen = dur(silent)
    voice = B / "voice" / f"{sid}.mp3" if sid in timing else None
    alen = (timing[sid]["duration"] + lead + tail) if voice else vlen
    total = max(vlen, alen)
    out = CLIPS / f"{sid}.mp4"
    if voice:
        run(["-i", str(silent), "-i", str(voice), "-filter_complex",
             f"[0:v]tpad=stop_mode=clone:stop_duration={total - vlen + 0.05:.2f}[v];"
             f"[1:a]adelay={int(lead * 1000)}|{int(lead * 1000)},apad,atrim=0:{total:.2f},aresample=48000[a]",
             "-map", "[v]", "-map", "[a]", "-t", f"{total:.2f}", "-c:v", "libx264", "-preset", "medium", "-crf", "19",
             "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-ac", "2", str(out)])
    else:
        run(["-i", str(silent), "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo", "-t", f"{total:.2f}",
             "-c:v", "copy", "-c:a", "aac", "-b:a", "160k", str(out)])
    return out, total, lead


def p(name):
    return CLIPS / name


plan = []
segment(p("a1.mp4"), "r1_landing", 0.3, 12.5, "The problem: small farms lose harvests to disease, far from help")
plan.append(scene("n1_hook", [p("a1.mp4")]))
segment(p("a2.mp4"), "r1_landing", 15.0, 27.5, "The idea: the device first, the cloud only with a yes")
plan.append(scene("n2_idea", [p("a2.mp4")]))
segment(p("a3.mp4"), "r2_onboard", 1.0, 41.0, "Start in seconds: no account, a 1 km area, your crops", speed=3.6, note="» ×3.6 fast-forward")
plan.append(scene("n3_onboard", [p("a3.mp4")]))
c4 = "Today: rules turn the forecast into decisions · Gemini Nano explains"
segment(p("a4a.mp4"), "r3_today", 8.0, 24.5, c4, speed=1.6)
segment(p("a4b.mp4"), "r3_today", 24.5, 42.5, c4, speed=6.0, note="» ×6   Gemini Nano on this laptop's CPU · 17.7 s real time")
segment(p("a4c.mp4"), "r3_today", 42.5, 54.0, c4)
plan.append(scene("n4_today", [p("a4a.mp4"), p("a4b.mp4"), p("a4c.mp4")]))
c5 = "Crop doctor on the device: ONLY_ON_DEVICE · the photo never leaves"
segment(p("a5a.mp4"), "r4_doctor", 1.0, 11.0, c5)
segment(p("a5b.mp4"), "r4_doctor", 11.0, 34.5, c5, speed=8.0, note="» ×8   Gemini Nano reading the leaf on this laptop's CPU · 23.7 s real time")
segment(p("a5c.mp4"), "r4_doctor", 34.5, 42.0, c5)
segment(p("a5d.mp4"), "r4_doctor", 44.0, 53.0, c5, speed=1.6)
plan.append(scene("n5_doctor", [p("a5a.mp4"), p("a5b.mp4"), p("a5c.mp4"), p("a5d.mp4")]))
c6 = "Cloud first, with consent: ONLY_IN_CLOUD · App Check verified"
segment(p("a6a.mp4"), "r5_cloud", 1.0, 11.0, c6, speed=2.0)
segment(p("a6b.mp4"), "r5_cloud", 11.0, 17.5, c6)
segment(p("a6c.mp4"), "r5_cloud", 18.5, 31.0, c6, speed=1.4)
plan.append(scene("n6_cloud", [p("a6a.mp4"), p("a6b.mp4"), p("a6c.mp4")]))
segment(p("a7.mp4"), "r6_map", 3.0, 12.5, "Neighbour outbreak map: an 11 km area, never a farm, never a name")
plan.append(scene("n7_map", [p("a7.mp4")]))
segment(p("a8.mp4"), "r7_market", 2.0, 24.0, "Shared market prices · farm log · Firestore offline", speed=1.8)
plan.append(scene("n8_market", [p("a8.mp4")]))
c9 = "Android: Firebase AI Logic SDK · Play Integrity · cloud after consent"
phone_seg(p("a9a.mp4"), "r9_android", 6.0, 30.0, c9, speed=1.8)
phone_seg(p("a9b.mp4"), "r9_android", 30.0, 48.0, c9, speed=4.0)
plan.append(scene("n9_android", [p("a9a.mp4"), p("a9b.mp4")]))
segment(p("a10.mp4"), "r8_close", 0.3, 12.0, "Remote Config · Analytics · built for every small farm")
plan.append(scene("n10_close", [p("a10.mp4")]))
end_card(p("end_v.mp4"))
run(["-i", str(p("end_v.mp4")), "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo", "-t", "4", "-c:v", "copy", "-c:a", "aac", "-b:a", "160k", str(p("end.mp4"))])

# Join scenes (re-encode audio/video timestamps cleanly) and write subtitles.
files = [f for f, _, _ in plan] + [p("end.mp4")]
concat_video(files, B / "conuco_nosubs.mp4")

srt, t = [], 0.0


def ts(x):
    h, m, s = int(x // 3600), int(x % 3600 // 60), x % 60
    return f"{h:02d}:{m:02d}:{s:06.3f}".replace(".", ",")


n = 1
for (f, total, lead), sid in zip(plan, [s for s in timing]):
    text, d = timing[sid]["text"], timing[sid]["duration"]
    words = text.split()
    chunks, cur = [], []
    for w_ in words:
        cur.append(w_)
        if len(" ".join(cur)) > 58 or w_.endswith((".", "?", "!")) and len(" ".join(cur)) > 25:
            chunks.append(" ".join(cur)); cur = []
    if cur:
        chunks.append(" ".join(cur))
    chars = sum(len(c) for c in chunks)
    start = t + lead
    for c in chunks:
        length = d * len(c) / chars
        srt.append(f"{n}\n{ts(start)} --> {ts(start + length - 0.05)}\n{c}\n")
        n += 1
        start += length
    t += dur(f)
(B / "conuco.srt").write_text("\n".join(srt), encoding="utf-8")

style = "FontName=Segoe UI Semibold,FontSize=13,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,BorderStyle=1,Outline=1.2,Shadow=0,MarginV=6,Alignment=2"
subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", "build/conuco_nosubs.mp4",
                "-vf", f"subtitles=build/conuco.srt:force_style='{style}'", "-c:v", "libx264", "-preset", "slow", "-crf", "18",
                "-pix_fmt", "yuv420p", "-c:a", "copy", "-movflags", "+faststart", "build/Conuco_BuildHybridAI_demo.mp4"],
               cwd=HERE, check=True)
print("done:", round(dur(B / "Conuco_BuildHybridAI_demo.mp4"), 1), "s")

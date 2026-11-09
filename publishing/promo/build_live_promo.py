"""Edit actual emulator screen recordings into the Arabic YouTube Short."""

import json
import math
import subprocess
from pathlib import Path

import build_promo as design
from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parent
WORK = ROOT / "work"
FPS = 24
OUTPUT = ROOT / "qari_fatura_youtube_short_live_ar.mp4"
SOURCES = {
    "camera": WORK / "live-capture.mp4",
    "import": WORK / "import-capture.mp4",
    "history": WORK / "history-stats-capture.mp4",
}


def run(args):
    subprocess.run(args, check=True)


def duration(path):
    info = json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", str(path)
    ], text=True))
    return float(info["format"]["duration"])


def normalize(source, name):
    """Fill Android screenrecord's sparse timestamps with held 24 fps frames."""
    out = WORK / f"live_normalized_{name}.mp4"
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
         "-map", "0:v:0", "-vf", f"fps={FPS},format=yuv420p", "-r", str(FPS),
         "-c:v", "libx264", "-preset", "veryfast", "-crf", "19", "-an", str(out)])
    return out


def render_base(index, title, subtitle):
    image = design.background()
    draw = ImageDraw.Draw(image)
    design.centered(draw, (540, 86), "قارئ فاتورة", 31, "#88E8CF", True)
    design.centered(draw, (540, 163), title, 64, "#FFFFFF", True)
    design.centered(draw, (540, 240), subtitle, 34, "#D6EDE9")
    shadow = Image.new("RGBA", design.SIZE, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle((98, 308, 982, 1863), radius=53, fill=(0, 0, 0, 115))
    image.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(29)))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((92, 299, 988, 1855), radius=53, fill="#F3F8F9")
    for n in range(6):
        x = 407 + n * 54
        draw.rounded_rectangle((x, 1880, x + 35, 1887), radius=4,
                               fill="#7CE9CD" if n == index else "#669994")
    out = WORK / f"live_base_{index + 1:02d}.png"
    image.convert("RGB").save(out)
    return out


def rounded_mask():
    mask = Image.new("L", (860, 1528), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 859, 1527), radius=35, fill=255)
    out = WORK / "live_mask.png"
    mask.save(out)
    return out


def fit(source, start, end, frames, name):
    out = WORK / f"live_fit_{name}.mp4"
    target = frames / FPS
    factor = target / (end - start)
    vf = (f"trim=start={start}:end={end},setpts=(PTS-STARTPTS)*{factor:.8f},"
          f"fps={FPS},tpad=stop_mode=clone:stop_duration=3,trim=duration={target:.8f},"
          "scale=860:1528:flags=lanczos,setsar=1,format=yuv420p")
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source),
         "-map", "0:v:0", "-vf", vf, "-frames:v", str(frames), "-r", str(FPS),
         "-c:v", "libx264", "-preset", "veryfast", "-crf", "19", "-an", str(out)])
    return out


def concat_video(paths, name):
    manifest = WORK / f"{name}.txt"
    manifest.write_text("".join(f"file '{path.as_posix()}'\n" for path in paths), encoding="utf-8")
    out = WORK / f"{name}.mp4"
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0",
         "-i", str(manifest), "-c", "copy", str(out)])
    return out


def composite(base, footage, mask, frames, name):
    out = WORK / f"live_scene_{name}.mp4"
    filters = ("[1:v]format=rgba[fg];[2:v]format=gray[mask];"
               "[fg][mask]alphamerge[rounded];"
               "[0:v][rounded]overlay=110:316:shortest=1,format=yuv420p[out]")
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
         "-loop", "1", "-framerate", str(FPS), "-i", str(base),
         "-i", str(footage), "-loop", "1", "-framerate", str(FPS), "-i", str(mask),
         "-filter_complex", filters, "-map", "[out]", "-frames:v", str(frames),
         "-r", str(FPS), "-c:v", "libx264", "-preset", "veryfast", "-crf", "19",
         "-an", str(out)])
    return out


def still(image, frames, name):
    path = WORK / f"live_{name}.png"
    image.convert("RGB").save(path)
    out = WORK / f"live_scene_{name}.mp4"
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-loop", "1",
         "-framerate", str(FPS), "-i", str(path),
         "-vf", f"zoompan=z='min(zoom+0.00034,1.045)':d=1:s=1080x1920:fps={FPS},format=yuv420p",
         "-frames:v", str(frames), "-r", str(FPS), "-c:v", "libx264",
         "-preset", "veryfast", "-crf", "19", "-an", str(out)])
    return out


def main():
    for source in SOURCES.values():
        if not source.is_file():
            raise SystemExit(f"Missing live recording: {source}")
    sources = {name: normalize(path, name) for name, path in SOURCES.items()}
    voice = sorted((WORK / "voice").glob("*.mp3"))
    if len(voice) != 7:
        raise SystemExit("Generate the seven narration clips first")
    voice_frames = [math.ceil((duration(path) + 0.42) * FPS) for path in voice]
    split = round(voice_frames[3] * 0.48)
    frames = voice_frames[:3] + [split, voice_frames[3] - split] + voice_frames[4:]
    mask = rounded_mask()

    pieces = [still(design.opening(), frames[0], "opening")]
    specs = [
        (0, "امسح رمز الفاتورة", "بالكاميرا مباشرة",
         fit(sources["camera"], 3.8, 5.4, frames[1], "camera")),
        (1, "أو اختر ملفًا", "صورة أو PDF من جهازك", None),
        (2, "النتيجة أمامك", "بيانات QR مقروءة بوضوح",
         fit(sources["camera"], 5.1, 8.2, frames[3], "result")),
        (3, "راجع التفاصيل", "البائع • التاريخ • الإجمالي • الضريبة",
         fit(sources["camera"], 28.5, 34.5, frames[4], "details")),
        (4, "ارجع لفواتيرك", "سجل مرتب لقراءاتك",
         fit(sources["history"], 1.0, 6.5, frames[5], "history")),
        (5, "تابع إحصاءاتك", "ملخصات تجمع فواتيرك",
         fit(sources["history"], 8.5, 15.7, frames[6], "statistics")),
    ]
    import_frames = [26, 28, frames[2] - 54]
    import_parts = [
        fit(sources["import"], 1.0, 3.8, import_frames[0], "import_a"),
        fit(sources["import"], 3.9, 5.2, import_frames[1], "import_b"),
        fit(sources["import"], 12.3, 15.8, import_frames[2], "import_c"),
    ]
    specs[1] = (*specs[1][:3], concat_video(import_parts, "live_import_sequence"))

    for i, title, subtitle, footage in specs:
        base = render_base(i, title, subtitle)
        pieces.append(composite(base, footage, mask, frames[i + 1], f"{i + 1:02d}"))
        print(f"Composited {title}", flush=True)
    pieces.append(still(design.ending(), frames[-1], "ending"))
    silent = concat_video(pieces, "live_silent")

    inputs = []
    filters = []
    for i, (path, count) in enumerate(zip(voice, voice_frames)):
        inputs += ["-i", str(path)]
        filters.append(f"[{i}:a]aresample=48000,apad=pad_dur=1,"
                       f"atrim=duration={count/FPS:.6f},asetpts=PTS-STARTPTS[a{i}]")
    filters.append("".join(f"[a{i}]" for i in range(7)) + "concat=n=7:v=0:a=1,alimiter=limit=0.93[a]")
    audio = WORK / "live_narration.m4a"
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *inputs,
         "-filter_complex", ";".join(filters), "-map", "[a]",
         "-c:a", "aac", "-b:a", "192k", str(audio)])
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(silent),
         "-i", str(audio), "-map", "0:v:0", "-map", "1:a:0", "-c", "copy",
         "-movflags", "+faststart", "-shortest", str(OUTPUT)])
    print(f"Ready: {OUTPUT}", flush=True)


if __name__ == "__main__":
    main()

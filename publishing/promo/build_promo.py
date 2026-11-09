"""Build a 9:16 Arabic promotional short from the app's real store screenshots."""

import json
import math
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parent.parent
sys.path.insert(0, str(ROOT / "_deps"))

import arabic_reshaper  # noqa: E402
from bidi.algorithm import get_display  # noqa: E402
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps  # noqa: E402


SIZE = (1080, 1920)
FPS = 24
FONT = Path(r"C:\Windows\Fonts\segoeui.ttf")
BOLD = Path(r"C:\Windows\Fonts\segoeuib.ttf")
SCREENSHOTS = PROJECT / "publishing" / "graphics" / "screenshots" / "ar"
ICON = PROJECT / "publishing" / "graphics" / "app-icon-512.png"
WORK = ROOT / "work"
OUTPUT = ROOT / "qari_fatura_youtube_short_ar.mp4"


def command(args):
    subprocess.run(args, check=True)


def seconds(path):
    result = subprocess.check_output(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", str(path)],
        text=True,
    )
    return float(json.loads(result)["format"]["duration"])


def rtl(text):
    return get_display(arabic_reshaper.reshape(text))


def font(size, bold=False):
    return ImageFont.truetype(str(BOLD if bold else FONT), size)


def centered(draw, xy, text, size, color, bold=False, max_width=980):
    f = font(size, bold)
    shaped = rtl(text)
    while draw.textbbox((0, 0), shaped, font=f)[2] > max_width and size > 26:
        size -= 2
        f = font(size, bold)
    draw.text(xy, shaped, font=f, fill=color, anchor="mm")


def background():
    image = Image.new("RGB", SIZE)
    pixels = image.load()
    top = (10, 42, 53)
    bottom = (5, 89, 86)
    for y in range(SIZE[1]):
        t = y / (SIZE[1] - 1)
        color = tuple(round(a * (1 - t) + b * t) for a, b in zip(top, bottom))
        for x in range(SIZE[0]):
            pixels[x, y] = color
    overlay = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    d.ellipse((-350, -250, 500, 600), fill=(30, 180, 160, 24))
    d.ellipse((570, 1240, 1450, 2130), fill=(132, 238, 209, 19))
    d.ellipse((810, 100, 1220, 510), outline=(182, 247, 226, 28), width=3)
    return Image.alpha_composite(image.convert("RGBA"), overlay)


def card(image, screenshot_name, highlight_import=False):
    shadow = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle((98, 308, 982, 1863), radius=53, fill=(0, 0, 0, 115))
    image.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(29)))

    d = ImageDraw.Draw(image)
    d.rounded_rectangle((92, 299, 988, 1855), radius=53, fill=(243, 248, 249, 255))
    shot = Image.open(SCREENSHOTS / screenshot_name).convert("RGB")
    shot = ImageOps.fit(shot, (860, 1529), method=Image.Resampling.LANCZOS)
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 859, 1528), radius=35, fill=255)
    image.paste(shot, (110, 316), mask)
    if highlight_import:
        glow = Image.new("RGBA", SIZE, (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        gd.ellipse((140, 575, 256, 691), outline=(2, 192, 160, 230), width=9)
        gd.ellipse((128, 563, 268, 703), outline=(2, 192, 160, 68), width=11)
        image.alpha_composite(glow)


def interior(index, title, subtitle, screenshot, highlight=False):
    image = background()
    d = ImageDraw.Draw(image)
    centered(d, (540, 86), "قارئ فاتورة", 31, "#88E8CF", True)
    centered(d, (540, 163), title, 64, "#FFFFFF", True)
    centered(d, (540, 240), subtitle, 34, "#D6EDE9")
    card(image, screenshot, highlight)
    for n in range(6):
        x = 407 + n * 54
        d.rounded_rectangle((x, 1880, x + 35, 1887), radius=4,
                            fill="#7CE9CD" if n == index else "#669994")
    return image.convert("RGB")


def opening():
    image = background()
    d = ImageDraw.Draw(image)
    d.rounded_rectangle((238, 490, 842, 1090), radius=124, fill=(255, 255, 255, 22))
    icon = Image.open(ICON).convert("RGB").resize((414, 414), Image.Resampling.LANCZOS)
    image.paste(icon, (333, 582))
    centered(d, (540, 1178), "فاتورتك السعودية", 80, "#FFFFFF", True)
    centered(d, (540, 1282), "بياناتها بين يديك", 49, "#A6EED9")
    d.rounded_rectangle((214, 1480, 866, 1583), radius=51, fill="#D7F7E9")
    centered(d, (540, 1532), "امسح  •  راجع  •  احتفظ", 36, "#07554F", True)
    centered(d, (540, 1790), "قارئ فاتورة", 33, "#B9D7D2")
    return image.convert("RGB")


def ending():
    image = background()
    d = ImageDraw.Draw(image)
    icon = Image.open(ICON).convert("RGB").resize((370, 370), Image.Resampling.LANCZOS)
    image.paste(icon, (355, 477))
    centered(d, (540, 984), "قارئ فاتورة", 87, "#FFFFFF", True)
    centered(d, (540, 1097), "اقرأ. احفظ. تابع.", 55, "#B8F2DF")
    d.rounded_rectangle((211, 1300, 869, 1426), radius=63, fill="#D7F7E9")
    centered(d, (540, 1363), "جرّب التطبيق الآن", 46, "#07554F", True)
    centered(d, (540, 1650), "قراءة محلية  •  بلا حساب", 34, "#C3DED9")
    return image.convert("RGB")


def make_scenes():
    scenes = [
        opening(),
        interior(0, "امسح رمز الفاتورة", "بالكاميرا مباشرة", "01-camera.png"),
        interior(1, "أو اختر ملفًا", "صورة أو PDF من جهازك", "01-camera.png", True),
        interior(2, "النتيجة أمامك", "بيانات QR مقروءة بوضوح", "02-result.png"),
        interior(3, "راجع التفاصيل", "البائع • التاريخ • الإجمالي • الضريبة", "03-invoice-details.png"),
        interior(4, "ارجع لفواتيرك", "سجل مرتب لقراءاتك", "04-history.png"),
        interior(5, "تابع إحصاءاتك", "ملخصات تجمع فواتيرك", "05-statistics.png"),
        ending(),
    ]
    out = WORK / "scenes"
    out.mkdir(parents=True, exist_ok=True)
    for i, scene in enumerate(scenes, 1):
        scene.save(out / f"{i:02d}.png", optimize=True)
    print(f"Rendered {len(scenes)} scenes", flush=True)


def build():
    WORK.mkdir(exist_ok=True)
    make_scenes()
    voice = sorted((WORK / "voice").glob("*.mp3"))
    if len(voice) != 7:
        raise SystemExit("Generate the seven narration clips with create_voiceover.py first")

    voice_frames = [math.ceil((seconds(path) + 0.42) * FPS) for path in voice]
    split = round(voice_frames[3] * 0.48)
    scene_frames = voice_frames[:3] + [split, voice_frames[3] - split] + voice_frames[4:]
    scene_paths = []
    for i, frames in enumerate(scene_frames, 1):
        source = WORK / "scenes" / f"{i:02d}.png"
        destination = WORK / f"scene_{i:02d}.mp4"
        duration = frames / FPS
        command([
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-loop", "1", "-framerate", str(FPS), "-i", str(source),
            "-vf", f"zoompan=z='min(zoom+0.00034,1.045)':d=1:s=1080x1920:fps={FPS},format=yuv420p",
            "-frames:v", str(frames), "-c:v", "libx264", "-preset", "veryfast",
            "-crf", "19", "-r", str(FPS), "-an", str(destination),
        ])
        scene_paths.append(destination)
        print(f"Encoded scene {i}/{len(scene_frames)} ({duration:.2f}s)", flush=True)

    manifest = WORK / "scenes.txt"
    manifest.write_text("".join(f"file '{path.as_posix()}'\n" for path in scene_paths), encoding="utf-8")
    silent_video = WORK / "silent.mp4"
    command(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0",
             "-i", str(manifest), "-c", "copy", str(silent_video)])

    audio_inputs = []
    filters = []
    for i, (path, frames) in enumerate(zip(voice, voice_frames)):
        audio_inputs.extend(["-i", str(path)])
        filters.append(f"[{i}:a]aresample=48000,apad=pad_dur=1,atrim=duration={frames/FPS:.6f},asetpts=PTS-STARTPTS[a{i}]")
    filters.append("".join(f"[a{i}]" for i in range(len(voice))) + f"concat=n={len(voice)}:v=0:a=1,alimiter=limit=0.93[a]")
    audio = WORK / "narration.m4a"
    command(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *audio_inputs,
             "-filter_complex", ";".join(filters), "-map", "[a]", "-c:a", "aac", "-b:a", "192k", str(audio)])
    command(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(silent_video),
             "-i", str(audio), "-map", "0:v:0", "-map", "1:a:0", "-c", "copy",
             "-movflags", "+faststart", "-shortest", str(OUTPUT)])
    print(f"Ready: {OUTPUT}", flush=True)


if __name__ == "__main__":
    build()

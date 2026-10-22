"""Validate Play image formats, then build a local preview and upload ZIP."""
from pathlib import Path
import html
import struct
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "publishing/graphics"
SHOTS = ["01-camera", "02-result", "03-invoice-details", "04-history", "05-statistics", "06-backup", "07-dark-mode", "08-manual-entry"]
LABELS = ["مسح الفاتورة بالكاميرا", "نتيجة الفحص والمشاركة", "تفاصيل الفاتورة", "السجل والتصدير", "الإحصاءات", "النسخ الاحتياطي", "المظهر الداكن", "الإدخال اليدوي"]
ENGLISH_LABELS = ["Scan an invoice", "Scan result and sharing", "Invoice details", "History and export", "Statistics", "Backup and restore", "Dark mode", "Manual Base64 entry"]


def verify(path, expected, color):
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", path
    width, height, depth, actual_color, compression, filtering, interlace = struct.unpack(">IIBBBBB", data[16:29])
    assert (width, height) == expected and depth == 8 and actual_color == color, path
    assert compression == filtering == interlace == 0, path
    offset = 8
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk = data[offset + 4:offset + 8 + length]
        checksum = struct.unpack(">I", data[offset + 8 + length:offset + 12 + length])[0]
        assert zlib.crc32(chunk) & 0xffffffff == checksum, path
        offset += length + 12
    assert offset == len(data), path


def main():
    images = [ART / "app-icon-512.png"]
    verify(images[0], (512, 512), 6)
    assert images[0].stat().st_size <= 1024 * 1024
    sections = []
    for language in ("ar", "en"):
        actual_shots = {path.stem for path in (ART / "screenshots" / language).glob("*.png")}
        assert actual_shots == set(SHOTS), f"Unexpected or missing screenshots for {language}: {actual_shots ^ set(SHOTS)}"
        feature = ART / f"feature-graphic-{language}-1024x500.png"
        verify(feature, (1024, 500), 2)
        images.append(feature)
        figures = []
        for name, label in zip(SHOTS, LABELS if language == "ar" else ENGLISH_LABELS):
            shot = ART / f"screenshots/{language}/{name}.png"
            verify(shot, (1080, 1920), 2)
            assert shot.stat().st_size < 8 * 1024 * 1024
            images.append(shot)
            figures.append(f'<figure><img src="{shot.relative_to(ART).as_posix()}" alt="{html.escape(label)}"><figcaption>{html.escape(label)}</figcaption></figure>')
        sections.append(f'<section><h2>{"العربية" if language == "ar" else "English"}</h2><img class="feature" src="{feature.name}" alt="الصورة الترويجية"><div class="shots">{"".join(figures)}</div></section>')
    preview = ART / "preview.html"
    preview.write_text('''<!doctype html><html lang="ar" dir="rtl"><meta charset="utf-8"><title>صور قارئ فاتورة</title>
<style>*{box-sizing:border-box}body{margin:0;padding:40px;background:#edf4f4;color:#102f3a;font-family:Tahoma,Arial,sans-serif}main{max-width:1280px;margin:auto}h1{font-size:32px}p{line-height:1.8}section{margin-top:44px}.feature{width:min(100%,1024px);display:block;border-radius:16px}.shots{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:24px;margin-top:28px}figure{margin:0}figure img{width:100%;display:block;border-radius:12px;box-shadow:0 8px 22px #102f3a15}figcaption{padding:14px 0;font-size:17px}@media(max-width:700px){body{padding:20px}.shots{grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}}</style>
<main><h1>صور صفحة Google Play — قارئ فاتورة</h1><p>معاينة فقط: ارفع ملفات PNG الأصلية إلى المتجر. جميع بيانات الفواتير تجريبية.</p>'''
        + "".join(sections) + "</main></html>", encoding="utf-8")
    archive = ART / "google-play-images.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as output:
        for path in images + [preview, ART / "README.md"]:
            output.write(path, path.relative_to(ART).as_posix())
    print(f"Verified {len(images)} PNG files. Created {preview.relative_to(ROOT)} and {archive.relative_to(ROOT)}")


if __name__ == "__main__":
    main()

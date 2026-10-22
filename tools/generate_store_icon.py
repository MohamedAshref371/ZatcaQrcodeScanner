"""Generate matching Android vector, monochrome and Play PNG icons without dependencies.
Run from the repository root: python tools/generate_store_icon.py
The QR motif is decorative, not an encoded invoice.
"""
from pathlib import Path
import argparse
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--store-only", action="store_true", help="Update Play artwork without rewriting Android launcher assets")
args = parser.parse_args()
DRAWABLE = ROOT / "app/src/main/res/drawable"
ART = ROOT / "publishing/graphics"
BACKGROUND = "#102F3A"
INK = "#087F8C"
PAPER = [(38, 28), (70, 28), (74, 32), (74, 78), (69, 75),
         (64, 78), (59, 75), (54, 78), (49, 75), (44, 78), (34, 74), (34, 32)]
RECTS = []
for x, y in [(40, 36), (58, 36), (40, 54)]:
    RECTS.extend([(x, y, 10, 2), (x, y+8, 10, 2), (x, y+2, 2, 6),
                  (x+8, y+2, 2, 6), (x+4, y+4, 2, 2)])
RECTS += [(58, 54, 4, 4), (64, 54, 4, 2), (66, 58, 2, 6),
          (58, 60, 4, 4), (62, 62, 4, 2), (40, 69, 28, 2)]

def rect_path(x, y, w, h):
    return f"M{x},{y}h{w}v{h}h{-w}z"

paper_path = "M" + "L".join(f"{x},{y}" for x, y in PAPER) + "z"
qr_path = " ".join(rect_path(*r) for r in RECTS)
header = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">'
ART.mkdir(parents=True, exist_ok=True)
if not args.store_only:
    DRAWABLE.mkdir(parents=True, exist_ok=True)
    (DRAWABLE / "ic_launcher_background.xml").write_text(
        f'{header}\n    <path android:fillColor="{BACKGROUND}" android:pathData="M0,0h108v108H0z" />\n</vector>\n', encoding="utf-8")
    (DRAWABLE / "ic_launcher_foreground.xml").write_text(
        f'{header}\n    <path android:fillColor="#FFFFFF" android:pathData="{paper_path}" />\n'
        f'    <path android:fillColor="{INK}" android:pathData="{qr_path}" />\n</vector>\n', encoding="utf-8")
    (DRAWABLE / "ic_launcher_monochrome.xml").write_text(
        f'{header}\n    <path android:fillColor="#FFFFFF" android:fillType="evenOdd" android:pathData="{paper_path} {qr_path}" />\n</vector>\n', encoding="utf-8")

# Play artwork has no adaptive-launcher masking, so use a larger, centered motif.
PAPER = [(54 + (x - 54) * 1.5, 54 + (y - 53) * 1.5) for x, y in PAPER]
RECTS = [(54 + (x - 54) * 1.5, 54 + (y - 53) * 1.5, w * 1.5, h * 1.5)
         for x, y, w, h in RECTS]
paper_path = "M" + "L".join(f"{x},{y}" for x, y in PAPER) + "z"
qr_path = " ".join(rect_path(*r) for r in RECTS)
(ART / "app-icon.svg").write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">'
    f'<path fill="{BACKGROUND}" d="M0 0h108v108H0z"/>'
    f'<path fill="white" d="{paper_path}"/><path fill="{INK}" d="{qr_path}"/></svg>\n', encoding="utf-8")

# Supersampling provides antialiasing while keeping the tool dependency-free.
N, AA = 512, 4
SIZE = N * AA
scale = SIZE / 108
bg = bytes.fromhex(BACKGROUND[1:])
ink = bytes.fromhex(INK[1:])
pixels = bytearray(bg * SIZE * SIZE)
def span(y, left, right, color):
    left, right = max(0, int(left)), min(SIZE, int(right))
    if right > left:
        start = (y * SIZE + left) * 3
        pixels[start:start + (right-left)*3] = color * (right-left)

points = [(x * scale, y * scale) for x, y in PAPER]
for y in range(SIZE):
    cuts = []
    for a, b in zip(points, points[1:] + points[:1]):
        if (a[1] <= y + 0.5 < b[1]) or (b[1] <= y + 0.5 < a[1]):
            cuts.append(a[0] + (y + 0.5 - a[1]) * (b[0] - a[0]) / (b[1] - a[1]))
    cuts.sort()
    for left, right in zip(cuts[::2], cuts[1::2]):
        span(y, left, right, b"\xff\xff\xff")
for x, y, w, h in RECTS:
    for row in range(int(y * scale), int((y+h) * scale)):
        span(row, x * scale, (x+w) * scale, ink)

raw = bytearray()
for y in range(N):
    raw.append(0)  # PNG scanline filter
    for x in range(N):
        for channel in range(3):
            total = sum(pixels[((y*AA+dy)*SIZE + x*AA+dx)*3+channel]
                        for dy in range(AA) for dx in range(AA))
            raw.append(total // (AA * AA))
        raw.append(255)  # Opaque alpha: Google Play requires a 32-bit PNG icon.
def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind+data) & 0xffffffff)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", N, N, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
(ART / "app-icon-512.png").write_bytes(png)
print("Generated SVG source and 512x512 Play icon." if args.store_only else
      "Generated Android vectors, SVG source and 512x512 Play icon.")

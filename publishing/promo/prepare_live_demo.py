"""Prepare a second synthetic invoice QR for the live image-import walkthrough."""

import base64
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "_deps"))

import qrcode  # noqa: E402


def main():
    fields = [
        (1, "مقهى تجريبي"),
        (2, "300000000000003"),
        (3, "2026-10-21T12:00:00"),
        (4, "57.50"),
        (5, "7.50"),
    ]
    payload = b"".join(bytes((tag, len(data))) + data
                       for tag, value in fields for data in [value.encode("utf-8")])
    encoded = base64.b64encode(payload).decode("ascii")
    image = qrcode.make(encoded, border=5).convert("RGB")
    out = ROOT / "work" / "demo-import-qr.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    image.resize((1200, 1200)).save(out)
    print(out)


if __name__ == "__main__":
    main()

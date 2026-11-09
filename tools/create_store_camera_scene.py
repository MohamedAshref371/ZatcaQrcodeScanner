"""Draw a synthetic invoice poster for the Android emulator's virtual scene camera.

Requires Pillow and qrcode. The QR matches a fixture in StoreScreenshotCapture.
This is a camera scene asset, never an overlay on the app screenshot.
"""
import argparse
import base64
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
import qrcode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", choices=("ar", "en"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    seller = "متجر تجريبي" if args.language == "ar" else "Sample Store"
    values = [(1, seller), (2, "300000000000003"), (3, "2026-10-20T10:30:00"),
              (4, "230.00"), (5, "30.00")]
    payload = b"".join(bytes((tag, len(data))) + data
                       for tag, value in values for data in [value.encode("utf-8")])
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=4, box_size=8)
    qr.add_data(base64.b64encode(payload).decode("ascii"))
    qr.make(fit=True)
    code = qr.make_image(fill_color="#142b32", back_color="white").convert("RGB")
    code = code.resize((480, 480), Image.Resampling.NEAREST)
    image = Image.new("RGB", (900, 1200), "#f7f5ed")
    draw = ImageDraw.Draw(image)
    font_path = Path("C:/Windows/Fonts/tahoma.ttf")
    font = lambda size: ImageFont.truetype(str(font_path), size)
    draw.rounded_rectangle((38, 30, 862, 1170), radius=20, fill="white")
    draw.rounded_rectangle((90, 92, 810, 104), radius=6, fill="#00796d")
    draw.text((450, 164), "SAMPLE INVOICE", font=font(44), fill="#153b43", anchor="mm")
    draw.text((450, 230), seller, font=font(36), fill="#00796d", anchor="mm")
    draw.text((450, 295), "VAT 300000000000003", font=font(27), fill="#526570", anchor="mm")
    draw.line((110, 343, 790, 343), fill="#e3eceb", width=3)
    draw.text((120, 393), "Invoice total", font=font(30), fill="#153b43")
    draw.text((780, 393), "230.00 SAR", font=font(32), fill="#153b43", anchor="ra")
    draw.text((120, 450), "VAT included", font=font(26), fill="#526570")
    draw.text((780, 450), "30.00 SAR", font=font(26), fill="#526570", anchor="ra")
    image.paste(code, (210, 525))
    draw.text((450, 1050), "DEMO DATA ONLY", font=font(28), fill="#00796d", anchor="mm")
    draw.text((450, 1105), "Not a real invoice", font=font(23), fill="#526570", anchor="mm")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    image.save(args.output)
    print(args.output)


if __name__ == "__main__":
    main()

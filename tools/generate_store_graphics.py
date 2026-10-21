"""Create editable, bilingual Google Play feature graphics using the existing icon.

No Python packages required. Render with Chrome/Edge (see graphics/README.md).
Screenshots are captured separately from the real Android UI.
"""
from pathlib import Path
import argparse
import html
import subprocess

ROOT = Path(__file__).resolve().parents[1]
GRAPHICS = ROOT / "publishing/graphics"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--browser", help="Path to Chrome or Edge, to also export PNGs")
    args = parser.parse_args()
    sources = GRAPHICS / "source"
    sources.mkdir(parents=True, exist_ok=True)
    icon = (GRAPHICS / "app-icon.svg").read_text(encoding="utf-8")
    for lang, title, subtitle, detail in [
        ("ar", "قارئ فاتورة", "اقرأ QR الفواتير السعودية", "تفاصيل واضحة · سجل فواتير · تصدير ومشاركة"),
        ("en", "Invoice QR<br>Reader", "Read Saudi invoice QR codes", "Invoice details · History · Export & share"),
    ]:
        content = f'''<!doctype html>
<html lang="{lang}" dir="{'rtl' if lang == 'ar' else 'ltr'}">
<meta charset="utf-8"><title>Invoice QR Reader — feature graphic</title>
<style>
* {{ box-sizing:border-box }}
html,body {{ margin:0;width:1024px;height:500px;overflow:hidden }}
body {{ color:#fff;font-family:Tahoma,"Segoe UI",Arial,sans-serif;
 background:radial-gradient(ellipse at 85% 15%,#14646c 0,transparent 54%),linear-gradient(125deg,#102f3a,#0c4149);position:relative }}
body::before {{ content:"";position:absolute;width:600px;height:600px;border:1px solid #ffffff12;border-radius:50%;inset-inline-end:-155px;top:-65px }}
body::after {{ content:"";position:absolute;width:455px;height:455px;border:1px solid #ffffff12;border-radius:50%;inset-inline-end:-83px;top:8px }}
.copy {{ position:absolute;inset-inline-start:64px;top:73px;width:560px;z-index:2 }}
.eyebrow {{ font-size:15px;letter-spacing:3px;color:#89d9cf;font-weight:bold;margin-bottom:20px;direction:ltr;unicode-bidi:embed }}
h1 {{ font-size:{'65' if lang == 'ar' else '57'}px;line-height:1.14;letter-spacing:-1px;margin:0 0 22px;font-weight:700 }}
.subtitle {{ font-size:27px;line-height:1.6;color:#e2f5f2;margin:0 0 18px }}
.rule {{ height:3px;width:48px;background:#72d7c6;margin-bottom:20px;border-radius:3px }}
.detail {{ font-size:17px;line-height:1.8;color:#b2d4d4;white-space:nowrap }}
.art {{ width:280px;height:280px;position:absolute;inset-inline-end:65px;top:110px;z-index:2;transform:rotate({'-7' if lang == 'ar' else '7'}deg) }}
.tile {{ width:100%;height:100%;border-radius:50px;overflow:hidden;box-shadow:0 25px 55px #001c2966,0 0 0 1px #ffffff18 }}
.tile svg {{ width:100%;height:100% }}
.corner {{ position:absolute;width:34px;height:34px;border-color:#80dfcc;border-style:solid;opacity:.85 }}
.tl {{ top:-18px;left:-18px;border-width:3px 0 0 3px;border-radius:10px 0 0 0 }}
.br {{ bottom:-18px;right:-18px;border-width:0 3px 3px 0;border-radius:0 0 10px 0 }}
</style>
<main class="copy"><div class="eyebrow">INVOICE QR</div><h1>{title}</h1>
<p class="subtitle">{html.escape(subtitle)}</p><div class="rule"></div><div class="detail">{html.escape(detail)}</div></main>
<div class="art"><div class="corner tl"></div><div class="tile">{icon}</div><div class="corner br"></div></div>
</html>'''
        source = sources / f"feature-graphic-{lang}.html"
        source.write_text(content, encoding="utf-8")
        if args.browser:
            target = GRAPHICS / f"feature-graphic-{lang}-1024x500.png"
            command = [args.browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                       "--no-first-run", "--no-default-browser-check", "--force-device-scale-factor=1",
                       f"--user-data-dir={ROOT / 'build/store-browser'}", "--window-size=1024,500",
                       f"--screenshot={target}", source.as_uri()]
            result = subprocess.run(command, capture_output=True, timeout=60)
            if result.returncode or not target.exists():
                raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
            print(target.relative_to(ROOT))
        else:
            print(source.relative_to(ROOT))


if __name__ == "__main__":
    main()

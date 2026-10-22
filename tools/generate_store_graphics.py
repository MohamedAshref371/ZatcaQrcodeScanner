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
    for lang, title, subtitle, features, footer in [
        ("ar", "قارئ فاتورة", "امسح QR الفواتير السعودية",
         ("مسح الفواتير", "سجل محفوظ", "إحصاءات وتصدير"), "مسح محلي، بدون حساب"),
        ("en", "Invoice QR<br>Reader", "Read Saudi invoice QR codes",
         ("Scan invoices", "Saved history", "Statistics & export"), "Local scanning. No account needed."),
    ]:
        screenshot = GRAPHICS / "screenshots" / lang / "02-result.png"
        if not screenshot.exists():
            raise FileNotFoundError(screenshot)
        feature_labels = "".join(f"<span>{html.escape(label)}</span>" for label in features)
        content = f'''<!doctype html>
<html lang="{lang}" dir="{'rtl' if lang == 'ar' else 'ltr'}">
<meta charset="utf-8"><title>Invoice QR Reader — feature graphic</title>
<style>
* {{ box-sizing:border-box }}
html,body {{ margin:0;width:1024px;height:500px;overflow:hidden }}
body {{ color:#fff;font-family:Tahoma,"Segoe UI",Arial,sans-serif;position:relative;
 background:radial-gradient(ellipse at {'20%' if lang == 'ar' else '80%'} 45%,#297d78 0,#174b50 30%,transparent 61%),linear-gradient(115deg,#102f3a,#0b3039) }}
body::before {{ content:"";position:absolute;width:446px;height:446px;border:1px solid #a0e4d51c;
 border-radius:50%;inset-inline-end:-13px;top:27px }}
.copy {{ position:absolute;inset-inline-start:44px;top:36px;width:550px;z-index:2 }}
.brand {{ display:flex;align-items:center;gap:14px;height:52px }}
.brand-icon {{ width:52px;height:52px;border-radius:13px;overflow:hidden;box-shadow:0 0 0 1px #ffffff18 }}
.brand-icon svg {{ width:100%;height:100%;display:block }}
.brand-label {{ font-size:16px;letter-spacing:3px;color:#95e0d3;font-weight:bold;direction:ltr }}
h1 {{ font-size:{'64' if lang == 'ar' else '56'}px;line-height:1.08;letter-spacing:-1px;margin:18px 0 14px;font-weight:700 }}
.subtitle {{ font-size:27px;line-height:1.5;color:#e2f5f2;margin:0 0 24px }}
.features {{ display:flex;gap:9px;flex-wrap:wrap }}
.features span {{ font-size:17px;line-height:1.5;padding:9px 13px;border:1px solid #9be8d52e;
 border-radius:10px;background:#ffffff09;color:#e0f2ee;white-space:nowrap }}
.footer {{ font-size:18px;line-height:1.5;color:#a6d9d0;margin:26px 0 0;padding-top:16px;border-top:1px solid #a6d9d026 }}
html[lang="ar"] h1 {{ font-size:72px;line-height:1.12;margin:26px 0 20px }}
html[lang="ar"] .subtitle {{ font-size:29px;line-height:1.6;margin-bottom:30px }}
html[lang="ar"] .features span {{ font-size:18px;padding:11px 13px }}
html[lang="ar"] .footer {{ font-size:20px;margin-top:34px;padding-top:20px }}
.art {{ width:320px;height:444px;position:absolute;inset-inline-end:50px;top:28px;
 border:7px solid #d4e8e4;border-radius:32px;overflow:hidden;background:#f3f6f8;
 box-shadow:0 24px 60px #001c2973,0 0 0 1px #ffffff38 }}
.art img {{ display:block;width:100%;height:auto }}
</style>
<main class="copy"><div class="brand"><div class="brand-icon">{icon}</div><span class="brand-label">INVOICE QR</span></div>
<h1>{title}</h1><p class="subtitle">{html.escape(subtitle)}</p>
<div class="features">{feature_labels}</div><p class="footer">{html.escape(footer)}</p></main>
<div class="art"><img src="../screenshots/{lang}/02-result.png" alt="{'واجهة نتيجة الفحص' if lang == 'ar' else 'Scan result screen'}"></div>
</html>'''
        source = sources / f"feature-graphic-{lang}.html"
        source.write_text(content, encoding="utf-8")
        if args.browser:
            target = GRAPHICS / f"feature-graphic-{lang}-1024x500.png"
            command = [args.browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                       "--no-first-run", "--no-default-browser-check", "--force-device-scale-factor=1",
                       "--run-all-compositor-stages-before-draw", "--virtual-time-budget=1200",
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

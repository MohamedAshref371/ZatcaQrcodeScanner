"""Prepare/check the bilingual Markdown policy for a public Gist. No network calls."""
import argparse
from pathlib import Path
import re
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
PUBLISHING = ROOT / "publishing"


def prepare(check=False):
    policies = [(PUBLISHING / f"privacy-policy-{language}.md").read_text(encoding="utf-8").strip()
                for language in ("ar", "en")]
    contacts = []
    for policy in policies:
        if re.search(r"\[(?:enter|developer|support|أدخل|اسم)[^]]*]", policy, re.IGNORECASE):
            raise ValueError("Complete the publisher/contact placeholders before preparing the public policy")
        emails = set(re.findall(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", policy))
        if not emails or "vStudio" not in policy or "com.poetscorner.invoiceqr" not in policy:
            raise ValueError("Policy must identify the publisher, app and a working contact address")
        contacts.append(emails)
    if contacts[0] != contacts[1]:
        raise ValueError("Arabic and English contact addresses do not match")
    result = (
        "# سياسة الخصوصية / Privacy policy\n\n"
        "قارئ فاتورة / Invoice QR Reader — vStudio\n\n"
        "العربية أولًا، وتليها النسخة الإنجليزية. Arabic first; English follows.\n\n"
        '<div dir="rtl">\n\n' + policies[0] + '\n\n</div>\n\n---\n\n'
        '<div dir="ltr">\n\n' + policies[1] + '\n\n</div>\n'
    )
    output = PUBLISHING / "privacy-policy.md"
    if check:
        if not output.exists() or output.read_text(encoding="utf-8") != result:
            raise ValueError("Public policy is out of date; run python tools/prepare_privacy.py")
        print("PASS: bilingual public policy matches both canonical policies; publisher and contact are complete.")
    else:
        output.write_text(result, encoding="utf-8", newline="\n")
        print(f"Prepared {output.relative_to(ROOT)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--apk", type=Path, help="Also verify the built APK contains the exact canonical policies")
    args = parser.parse_args()
    prepare(args.check)
    if args.apk:
        with ZipFile(args.apk) as apk:
            for language in ("ar", "en"):
                filename = f"privacy-policy-{language}.md"
                if apk.read(f"assets/{filename}") != (PUBLISHING / filename).read_bytes():
                    raise ValueError(f"Packaged policy is out of date: {filename}")
        print("PASS: APK privacy assets match the canonical policy files byte-for-byte.")

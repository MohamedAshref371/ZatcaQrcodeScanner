"""Audit translation coverage and format arguments. Run from any directory."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
def strings(folder):
    elements = ET.parse(RES / folder / "strings.xml").getroot().findall("string")
    result = {node.attrib["name"]: node.text or "" for node in elements}
    assert len(result) == len(elements), f"Duplicate keys in {folder}"
    return result

default, ar = (strings(folder) for folder in ("values", "values-ar"))
assert default.keys() == ar.keys(), "Translation keys do not match"
format_args = re.compile(r"%(?:\d+\$)?[dsf]")
for key, text in default.items():
    assert text.strip() and ar[key].strip(), f"Empty translation: {key}"
    assert sorted(format_args.findall(text)) == sorted(format_args.findall(ar[key])), f"Format mismatch: {key}"
    assert not re.search(r"[\u0600-\u06ff]", text), f"Arabic text in English resource: {key}"

sources = list((ROOT / "app/src/main/java").rglob("*.kt"))
references = set()
for path in sources:
    source = path.read_text(encoding="utf-8")
    references.update(re.findall(r"R\.string\.(\w+)", source))
    assert not re.search(r"[\u0600-\u06ff]", source), f"Hardcoded Arabic in {path}"
    assert not re.search(r'\bText\(\s*"', source), f"Hardcoded UI Text in {path}"
assert references <= default.keys(), f"Missing resources: {references - default.keys()}"
activity = (ROOT / "app/src/main/java/com/poetscorner/zatcaqrcodescanner/MainActivity.kt").read_text(encoding="utf-8")
assert "LayoutDirection.Rtl" not in activity, "Activity forces RTL"
print(f"PASS: {len(default)} translated keys, matching format arguments, no hardcoded Arabic or direct Text literals, all resource references present.")

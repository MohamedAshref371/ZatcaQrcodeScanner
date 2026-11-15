import json
from pathlib import Path
import shutil
import sys

attachments = Path(sys.argv[1])
output = Path(sys.argv[2])
manifest = json.loads((attachments / "manifest.json").read_text(encoding="utf-8"))
output.mkdir(parents=True, exist_ok=True)
expected = ("invoice-entry", "invoice-valid", "invoice-details", "invoice-invalid")
found = set()
for test in manifest:
    for item in test["attachments"]:
        name = item.get("suggestedHumanReadableName", "")
        filename = item.get("exportedFileName", "")
        for prefix in expected:
            if name.startswith(prefix) and filename.endswith(".png"):
                shutil.copyfile(attachments / filename, output / f"{prefix}.png")
                found.add(prefix)
if found != set(expected):
    raise SystemExit(f"Missing expected screenshots: {', '.join(sorted(set(expected) - found))}")

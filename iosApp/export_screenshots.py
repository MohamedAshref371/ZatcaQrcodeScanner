import json
from pathlib import Path
import shutil
import sys

attachments = Path(sys.argv[1])
output = Path(sys.argv[2])
manifest = json.loads((attachments / "manifest.json").read_text(encoding="utf-8"))
output.mkdir(parents=True, exist_ok=True)
expected = (
    "01-scanner", "02-scan-sources", "03-history-empty", "04-manual-entry",
    "05-invoice-result", "06-invoice-edit", "07-invoice-details", "08-history",
    "09-history-filters", "10-statistics", "11-statistics-filters",
    "12-statistics-calculations", "13-statistics-breakdown", "14-statistics-group-invoices",
    "15-invoice-advanced", "16-settings", "17-settings-zxing", "18-settings-language",
    "19-settings-appearance", "20-settings-backup", "21-invoice-verification",
    "22-invoice-checks", "23-invoice-raw", "24-history-delete-period",
)
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
print(f"Captured {len(found)} iPhone screenshots.")

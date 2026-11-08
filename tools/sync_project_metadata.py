"""Keep the active project and publishing guides aligned with Gradle and Room."""

import argparse
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app/build.gradle.kts"
DATABASE = ROOT / "app/src/main/java/com/poetscorner/invoiceqr/data/LocalData.kt"


def one(pattern: str, source: str, label: str) -> str:
    matches = re.findall(pattern, source, re.MULTILINE)
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one {label}; found {len(matches)}")
    return matches[0]


def replace_line(source: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(
        pattern,
        lambda match: replacement + ("\r" if match.group(0).endswith("\r") else ""),
        source,
        flags=re.MULTILINE,
    )
    if count != 1:
        raise ValueError(f"Expected exactly one {label}; found {count}")
    return updated


def guide_updates(version: str, code: int, schema: int, migrations: list[tuple[int, int]]):
    migration_en = ", ".join(f"{start} to {end}" for start, end in migrations)
    migration_ar = " و".join(f"{start} إلى {end}" for start, end in migrations)
    migration_sentence_en = (f"Migrations from {migration_en} are implemented. " if migrations else "No migrations are configured. ")
    migration_sentence_ar = (f"مع ترحيلات من {migration_ar}. " if migrations else "دون ترحيلات. ")
    return {
        "README.md": [
            (r"The current version is `[^`]+`, version code `\d+`\.",
             f"The current version is `{version}`, version code `{code}`.", "English version"),
            (r"^The current (?:experimental )?database schema is version \*\*\d+\*\*\..*$",
             f"The current database schema is version **{schema}**. "
             f"{migration_sentence_en}Settings and review usage are stored independently from history.",
             "English schema"),
        ],
        "README-ar.md": [
            (r"الإصدار الحالي `[^`]+` ورقم البناء `\d+`\.",
             f"الإصدار الحالي `{version}` ورقم البناء `{code}`.", "Arabic version"),
            (r"^رقم مخطط قاعدة البيانات التجريبي الحالي هو \*\*\d+\*\*،.*$|^رقم مخطط قاعدة البيانات الحالي هو \*\*\d+\*\*،.*$",
             f"رقم مخطط قاعدة البيانات الحالي هو **{schema}**، {migration_sentence_ar}"
             "تُخزّن الإعدادات واستخدام التقييم مستقلَّين عن السجل.", "Arabic schema"),
        ],
        "publishing/README.md": [
            (r"^\| الإصدار في المشروع \|.*\|\r?$",
             f"| الإصدار في المشروع | `{version}`، ورقم البناء `{code}` (ارفع الرقم قبل نشر التعديل التالي) |",
             "publishing version"),
            (r"^\| مخطط قاعدة البيانات \|.*\|\r?$",
             f"| مخطط قاعدة البيانات | `{schema}`؛ {migration_sentence_ar.strip()} |", "publishing schema"),
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if the guides need updating")
    args = parser.parse_args()

    gradle = GRADLE.read_text(encoding="utf-8")
    database = DATABASE.read_text(encoding="utf-8")
    version = one(r'^\s*versionName = "([^"]+)"\s*$', gradle, "versionName")
    code = int(one(r"^\s*versionCode = (\d+)\s*$", gradle, "versionCode"))
    schema = int(one(r"^@Database\(entities = \[ScanEntity::class\], version = (\d+),", database, "Room schema"))
    migrations = sorted({(int(start), int(end)) for start, end in re.findall(r"\bMIGRATION_(\d+)_(\d+)\b", database)})
    changed = []
    for relative, replacements in guide_updates(version, code, schema, migrations).items():
        path = ROOT / relative
        original = path.read_bytes().decode("utf-8")
        updated = original
        for pattern, replacement, label in replacements:
            updated = replace_line(updated, pattern, replacement, label)
        if updated != original:
            changed.append(relative)
            if not args.check:
                path.write_bytes(updated.encode("utf-8"))

    if args.check and changed:
        print("Outdated project metadata in: " + ", ".join(changed), file=sys.stderr)
        return 1
    print(f"Project metadata {version} ({code}), Room {schema}: " +
          ("up to date" if not changed else "updated " + ", ".join(changed)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

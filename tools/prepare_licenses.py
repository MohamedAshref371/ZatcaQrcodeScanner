"""Build the offline license catalog from the resolved release artifacts.

First run Gradle with -I tools/export-license-inputs.gradle :app:exportLicenseInputs.
No network access: POMs and archive notices come from the resolved Gradle cache.
Unknown licenses fail rather than silently disappearing from the catalog.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/NOTICE.txt"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}

PROPRIETARY_LICENSES = {
    "Android Software Development Kit License", "ML Kit Terms of Service",
    "Play Core Software Development Kit Terms of Service",
}

def catalog(inputs):
    texts, entries = {}, {}

    def add(name, text):
        text = text.replace("\r\n", "\n").strip()
        if not text:
            return
        key = hashlib.sha256(text.encode()).hexdigest()[:16]
        texts[key] = text
        entries.setdefault(name, set()).add(key)

    def archive_notices(name, data):
        with zipfile.ZipFile(data) as archive:
            names = archive.namelist()
            if "third_party_licenses.json" in names:
                index = json.loads(archive.read("third_party_licenses.json"))
                content = archive.read("third_party_licenses.txt")
                for title, span in index.items():
                    start, length = span["start"], span["length"]
                    assert 0 <= start < start + length <= len(content), title
                    add(title, content[start:start + length].decode("utf-8"))
            for member in names:
                leaf = member.rsplit("/", 1)[-1]
                # Google provides a byte-indexed OSS table; its root LICENSE is a
                # second concatenation of those notices plus proprietary SDK terms.
                if member == "LICENSE" and "third_party_licenses.json" in names:
                    continue
                if re.fullmatch(r"(LICENSE|NOTICE|COPYING)([._-].*)?", leaf, re.I):
                    add(name, archive.read(member).decode("utf-8"))
                elif member == "classes.jar" or (member.startswith("libs/") and member.endswith(".jar")):
                    archive_notices(name, io.BytesIO(archive.read(member)))

    caches = [Path(inputs["cache"]) / "caches/modules-2/files-2.1",
              ROOT / ".gradle-user-home/caches/modules-2/files-2.1"]

    def read_pom(group, module, version):
        poms = [p for cache in caches for p in (cache / group / module / version).glob("*/*.pom")]
        if not poms:
            raise ValueError(f"Missing cached POM: {group}:{module}:{version}")
        return ET.parse(poms[0]).getroot()

    def declared_licenses(pom):
        licenses = pom.findall("m:licenses/m:license", NS)
        parent = pom.find("m:parent", NS)
        if not licenses and parent is not None:
            return declared_licenses(read_pom(*(parent.findtext(f"m:{field}", "", NS)
                                               for field in ("groupId", "artifactId", "version"))))
        return licenses

    apache = (ROOT / "tools/licenses/Apache-2.0.txt").read_text(encoding="utf-8")
    for artifact in inputs["artifacts"]:
        coordinate = artifact["id"]
        group, module, version = coordinate.split(":")
        pom = read_pom(group, module, version)
        licenses = declared_licenses(pom)
        archive_notices(coordinate, Path(artifact["file"]))
        if coordinate == "io.github.zxing-cpp:android:3.1.1":
            add("Android native runtime (LLVM)",
                (ROOT / "tools/licenses/LLVM-NOTICE.txt").read_text(encoding="utf-8"))
        if coordinate == "javax.inject:javax.inject:1":
            # JSR 330 is also identified in the Google SDK third-party notices.
            add(coordinate, apache)
            continue
        if not licenses:
            raise ValueError(f"No declared license: {coordinate}")
        for license in licenses:
            label = license.findtext("m:name", "", NS)
            url = license.findtext("m:url", "", NS)
            if "apache" in (label + url).lower() and "2" in label + url:
                add(coordinate, apache)
            elif label in PROPRIETARY_LICENSES:
                # Proprietary Google SDK; its bundled OSS notices are read below.
                pass
            elif label == "BSD-3-Clause" and coordinate in {
                "androidx.camera:camera-core:1.6.2",
                "androidx.datastore:datastore-preferences-external-protobuf:1.3.0",
            }:
                source = "libyuv-LICENSE.txt" if module == "camera-core" else "protobuf-LICENSE.txt"
                add(coordinate, (ROOT / "tools/licenses" / source).read_text(encoding="utf-8"))
            elif label == "The MIT License" and coordinate == "org.checkerframework:checker-qual:3.43.0":
                if coordinate not in entries:
                    raise ValueError(f"Missing embedded MIT notice: {coordinate}")
            else:
                raise ValueError(f"Review license for {coordinate}: {label} {url}")
    return {"texts": texts, "entries": entries}


def render_notices(result):
    sections = ["Third-party licenses and notices"]
    for ref, text in sorted(result["texts"].items()):
        names = sorted(name for name, refs in result["entries"].items() if ref in refs)
        sections.append("=" * 72 + "\n" + "\n".join(names) + "\n\n" + text)
    return "\n\n".join(sections) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    inputs = json.loads((ROOT / "app/build/license-inputs.json").read_text(encoding="utf-8"))
    result = catalog(inputs)
    content = render_notices(result)
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text(encoding="utf-8") != content:
            raise SystemExit("Bundled notices are stale; run python tools/prepare_licenses.py")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(content, encoding="utf-8", newline="\n")
    print(f'{len(inputs["artifacts"])} artifacts, {len(result["entries"])} entries, '
          f'{len(result["texts"])} unique texts, {len(content.encode()):,} bytes')


if __name__ == "__main__":
    main()

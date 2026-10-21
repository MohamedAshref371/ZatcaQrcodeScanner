"""Package available, matching native symbols without requiring an installed NDK.

Some prebuilt dependencies retain a symbol table, while others ship stripped.
The archive contains ONLY libraries with real symbols, with their original ELF
build IDs. It never fabricates symbols or modifies a signed app bundle.
"""
from pathlib import Path
import argparse
import json
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def elf_sections(data):
    if data[:4] != b"\x7fELF":
        raise ValueError("Not an ELF library")
    endian = "<" if data[5] == 1 else ">"
    if data[4] == 2:
        offset = struct.unpack_from(endian + "Q", data, 40)[0]
        size, count, names_index = struct.unpack_from(endian + "HHH", data, 58)
        layout = endian + "IIQQQQIIQQ"
    elif data[4] == 1:
        offset = struct.unpack_from(endian + "I", data, 32)[0]
        size, count, names_index = struct.unpack_from(endian + "HHH", data, 46)
        layout = endian + "IIIIIIIIII"
    else:
        raise ValueError("Unknown ELF class")
    headers = [struct.unpack_from(layout, data, offset + index * size) for index in range(count)]
    strings = headers[names_index]
    names = data[strings[4]:strings[4] + strings[5]]
    return {
        names[section[0]:].split(b"\0", 1)[0].decode("ascii"):
        data[section[4]:section[4] + section[5]]
        for section in headers
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, default=ROOT / "app/build/outputs/bundle/release/app-release.aab")
    args = parser.parse_args()
    merged = ROOT / "app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib"
    output = ROOT / "app/build/outputs/native-debug-symbols/release"
    output.mkdir(parents=True, exist_ok=True)
    included, stripped = [], []
    payloads = {}
    with zipfile.ZipFile(args.bundle) as bundle:
        for path in sorted(merged.glob("*/*.so")):
            name = path.relative_to(merged).as_posix()
            data = path.read_bytes()
            sections = elf_sections(data)
            if not sections.get(".symtab") and not sections.get(".debug_info"):
                stripped.append(name)
                continue
            packaged_data = bundle.read("base/lib/" + name)
            packaged = elf_sections(packaged_data)
            build_id = sections.get(".note.gnu.build-id")
            if data != packaged_data and (not build_id or build_id != packaged.get(".note.gnu.build-id")):
                raise ValueError(f"Native build ID does not match the AAB: {name}")
            payloads[name] = data
            included.append(name)
    if not payloads:
        raise SystemExit("No usable symbols found; obtain matching symbols from the library publishers.")
    target = output / "native-debug-symbols.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in payloads.items():
            archive.writestr(name, data)
    report = {"bundle": str(args.bundle.resolve()), "included": included, "already_stripped": stripped}
    (output / "coverage.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Packaged {len(included)} native binaries with real symbols; {len(stripped)} already stripped.")
    print(target.relative_to(ROOT))


if __name__ == "__main__":
    main()

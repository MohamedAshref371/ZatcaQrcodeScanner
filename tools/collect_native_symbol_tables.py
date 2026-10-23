"""Supply real prebuilt ELF symbol tables to AGP before AAB packaging/signing."""
import argparse
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from prepare_native_symbols import elf_sections


def collect(merged, existing, output):
    output.mkdir(parents=True, exist_ok=True)
    # Remove only this task's generated files so removed dependencies cannot linger.
    for previous in output.rglob("*.so.sym"):
        previous.unlink()
    for directory in existing:
        if not directory.is_dir():
            continue
        for source in directory.rglob("*.so.sym"):
            target = output / source.relative_to(directory)
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.exists() and target.read_bytes() != source.read_bytes():
                raise ValueError(f"Conflicting native symbols: {target.name}")
            shutil.copyfile(source, target)

    included, stripped = 0, 0
    for library in sorted(merged.rglob("*.so")):
        data = library.read_bytes()
        sections = elf_sections(data)
        if not sections.get(".symtab"):
            stripped += 1
            continue
        target = output / library.parent.name / (library.name + ".sym")
        if target.exists():
            generated = elf_sections(target.read_bytes())
            build_id = sections.get(".note.gnu.build-id")
            if not generated.get(".symtab") or (
                target.read_bytes() != data
                and (not build_id or build_id != generated.get(".note.gnu.build-id"))
            ):
                raise ValueError(f"Native symbols do not match merged library: {library}")
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(library, target)
        included += 1
    print(f"Native symbols: {included} matching prebuilt libraries; {stripped} already stripped.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--merged-libs", required=True, type=Path)
    parser.add_argument("--existing-metadata", action="append", default=[], type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    collect(args.merged_libs, args.existing_metadata, args.output)


if __name__ == "__main__":
    main()

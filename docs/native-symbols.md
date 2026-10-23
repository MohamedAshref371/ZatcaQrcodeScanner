# Native symbols for release builds

Release builds keep `ndk.debugSymbolLevel = "SYMBOL_TABLE"`. The
`collectReleaseNativeSymbolTables` task transforms AGP's public
`MultipleArtifact.NATIVE_SYMBOL_TABLES` artifact before packaging and signing.
It preserves AGP-generated symbol tables and supplements them with real symbol
tables retained in the merged prebuilt libraries, without requiring an installed
NDK. Removed libraries cannot leave stale symbol files in the generated output.
Conflicting symbols or mismatching ELF build IDs fail the build.

The collector requires Python 3 on PATH. To use a specific installation, set the
Gradle property `nativeSymbolsPython` to its executable path.

## Build and upload

- `:app:bundleRelease` automatically includes available symbols under
  `BUNDLE-METADATA/com.android.tools.build.debugsymbols/<ABI>/<library>.so.sym`.
  Android Studio's signed bundle build uses the same artifact pipeline, so symbols
  are included before the bundle is signed. No separate symbols upload is needed
  for these embedded files.
- `:app:assembleRelease` also generates the standalone
  `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip`.
- `python tools/prepare_native_symbols.py` can regenerate that ZIP and
  `coverage.json` against the current AAB. It checks library contents/build IDs,
  verifies embedded symbols when present, and records the bundle SHA-256.
  For a different bundle, pass `--bundle <path>`; symbols must match that bundle.

The old signed release under `app/release` is preserved. The command-line bundle
is unsigned unless a release signing configuration is provided.

## Coverage and verification

For 1.0.3 (build 6), four ABI versions of `libdatastore_shared_counter.so` retain
usable symbols. Sixteen other native files from CameraX, AndroidX Graphics, and
ZXing-C++ are already stripped. Their missing symbols require matching files from
their publishers; packaging cannot recreate them. This remains partial coverage
and does not guarantee disappearance of every Play Console symbols warning.

The collector was checked with actual ELF libraries for fallback collection,
stale-file removal, preservation of existing metadata, and rejection of symbol
files from DataStore 1.2.0 when collecting DataStore 1.2.1.

Reference: https://developer.android.com/build/include-native-symbols

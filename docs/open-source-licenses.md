# Open source notices

The application has no open source licenses item or screen. Complete license
texts and source attributions are bundled in the human-readable asset
`app/src/main/assets/NOTICE.txt`, with identical texts stored once. The build
adds no runtime library or network request for licensing.

## Updating dependencies

After changing runtime dependencies, regenerate and review the bundled notices:

```powershell
./gradlew.bat -I tools/export-license-inputs.gradle :app:exportLicenseInputs
python tools/prepare_licenses.py
python tools/prepare_licenses.py --check
./gradlew.bat :app:assembleRelease :app:lintRelease
```

Use your usual Gradle user home (`-g` if needed). The Python generator reads the
exported cache and the repository-local `.gradle-user-home` cache. It requires
the dependencies' POMs, including parents declaring inherited licenses, to be
cached. If a POM is missing, resolve/download that POM before regenerating.
Unknown or missing license declarations fail for review. The generated notice
file is committed so normal app builds need no license download or Python runtime.
`--check` compares the notice file with the exported inputs; export fresh inputs
first when checking a dependency update.

## Inputs and review

- All artifacts in `releaseRuntimeClasspath`, including transitive dependencies;
  build plugins, test libraries, and debug-only tools are excluded.
- Apache 2.0 declarations, including inherited POM licenses, use the common text
  in `tools/licenses/Apache-2.0.txt` (canonical Apache text distributed with Kotlin).
- LICENSE/NOTICE/COPYING files in JARs, AARs and their nested JARs are retained.
- Google's `third_party_licenses.json` supplies byte offsets into
  `third_party_licenses.txt`. Each referenced notice is retained under its
  supplied name. Google's root LICENSE is a second aggregate plus SDK terms,
  so its indexed OSS notices are used instead. Proprietary Google SDKs are not
  presented as open source merely because they include open source components.
- Checker Framework's MIT notice comes from its own archive. JSR 330
  (`javax.inject:1`) has no POM license declaration; Apache 2.0 is also identified
  in Google's embedded JSR 330 notice.
- CameraX 1.6.2 declares Apache 2.0 and BSD-3-Clause; its embedded license only
  supplies Apache. The libyuv BSD notice is retained separately from
  [upstream LICENSE](https://github.com/lemenkov/libyuv/blob/master/LICENSE).
- DataStore's external protobuf 1.3.0 declares BSD-3-Clause. Its archive omits
  the copyright preamble, so the complete
  [protobuf notice](https://github.com/protocolbuffers/protobuf/blob/v28.2/LICENSE)
  is included in addition to the embedded text.
- ZXing-C++ Android 3.1.1 declares Apache 2.0 and has no separate NOTICE in its
  release tree. Its [Android build](https://github.com/zxing-cpp/zxing-cpp/blob/v3.1.1/wrappers/android/zxingcpp/build.gradle.kts)
  disables writers, so Zint writer dependencies do not apply. The LLVM runtime
  notices (including LLVM exceptions, libc++, libc++abi and compiler runtime)
  are retained from Android NDK 28.2.13676358's toolchain NOTICE as supplemental
  native runtime attribution. These supplemental files need review when native
  libraries change; the generator pins the corresponding current coordinates.

The Google notice format and inclusion workflow are documented in
[Google's official guide](https://developers.google.com/android/guides/opensource).
This inventory concerns the currently resolved binary dependencies; it does not
assign an open source license to the application's own code.

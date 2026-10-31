# Build toolchain upgrade validation

Date: 2026-10-24. App version: 1.0.4 (7).

## Configuration

- Android Gradle Plugin: 9.0.1 -> 9.2.1.
- Gradle: 9.1.0 -> 9.4.1, including regenerated official wrapper scripts/JAR.
- Distribution SHA-256 verified against the official Gradle checksum:
  `2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb`.
- Compile SDK: 36 -> 37, resolving the official `platforms;android-37.0`
  SDK package. Installation uses the already accepted local Android SDK license.
- Target SDK remains 36; minimum SDK remains 26.
- AGP 9.2.1's published POM selects Kotlin Gradle Plugin 2.2.10, matching the
  existing Compose compiler plugin. KSP 2.3.12 is retained and verified through
  the Room processor build. No Core, Lifecycle, or Compose library update is
  bundled into this toolchain change.
- Build/runtime validation uses the installed JBR 21.0.8.

The first wrapper regeneration finished successfully inside Gradle but the old
Windows batch launcher then resumed at an offset changed by its own regeneration
and returned 9009. Subsequent invocations use the regenerated launcher; their
results determine validation status rather than that first launcher's exit.

## Android Studio requirement

Installed Android Studio is Otter 3 / 2025.2.3, which officially supports AGP only
through 9.0. It is incompatible with this project's upgraded AGP for IDE sync.
Use **Android Studio Panda 4 / 2025.3.4 or newer** with AGP 9.2 support. The
existing IDE installation was inspected; an IDE upgrade is still a user setup
step. Command-line validation does not establish that old IDE sync works.

Both READMEs now state the new IDE, Gradle, and SDK requirements.

## Verification

All device checks use the separate API 36.1 validation emulator.

- Fresh regenerated wrapper reports Gradle 9.4.1 and JBR 21.0.8.
- SDK Platform 37.0 revision 2 installed successfully.
- `testDebugUnitTest`: 72 passed, zero failures/errors/skips.
- `lintRelease`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease`,
  and `bundleRelease`: passed (141 tasks executed in the combined validation).
- Room/KSP processing completed with no schema changes.
- Device tests: 28 passed, zero failures/skips. Covered actual camera streaming
  and zoom, activity recreation, edge-to-edge theme/keyboard checks, real ML Kit
  query/decoding, database protection, backup rollback, saved filters, privacy
  intent handling, supporter preview, result restoration, and Arabic/English.
- The unsigned optimized release APK was copied and debug-signed under
  `build/toolchain-upgrade/release-smoke.apk` for emulator testing. Cold launch
  succeeded; UIAutomator confirmed all five navigation tabs rendered. The
  captured log had no fatal exception or class-loading/linkage errors.
- APK badging confirms compile SDK 37, minimum SDK 26, target SDK 36.
- Output release APK/AAB are unsigned. No physical device installation or Play
  Console upload was performed.

### Lint

Fresh result: **0 errors, 12 warnings**, compared with 10 before this toolchain
upgrade. There are seven library update notices, two tooling update notices
(newer AGP and Gradle), one Kotlin Compose plugin update notice, one `OldTargetApi`
notice, and the intentionally retained plural warning. The new target notice is
expected because target SDK 36 is deliberately retained while compiling against
37. The newer Gradle notice is also retained; the chosen version matches the
AGP 9.2 compatibility table. No warnings were suppressed.

### Native symbol coverage

The custom native-symbol collection/packaging task ran successfully, but reported
20 merged libraries already stripped and no usable symbol tables. The resulting
AAB has zero debug-symbol entries. The older signed bundle in `app/release`
contains four DataStore symbol entries, but predates the dependency updates.

Checking the current DataStore 1.3.0 AAR confirms its four published JNI binaries
already lack both `.symtab` and `.debug_info`. Symbols cannot be recovered from
those published binaries by changing AGP. Matching publisher symbol packages or
unstripped builds are required for native symbol coverage; an upload warning may
remain. The older bundle's symbol files should not be reused for new binaries.

Logs: `logs/build-toolchain-version.log`, `logs/build-toolchain-wrapper.log`,
`logs/build-toolchain-validation.log`, and `build/toolchain-upgrade/` (local generated
files).

## Scope and limits

Keeping target SDK 36 separates compiler/tooling upgrades from Android 17
target-dependent behavior changes. This does not validate Android 17 targeting,
new APIs on old devices, physical camera focus/flash, or Play billing purchases.
The previously identified landscape cutout issue is not fixed by this work.

References:

- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility
- https://services.gradle.org/distributions/gradle-9.4.1-bin.zip.sha256

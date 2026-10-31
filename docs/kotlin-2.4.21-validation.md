# Kotlin and Compose compiler 2.4.21 validation

Date: 2026-10-25. Application version: 1.0.5 (8).

## Change and resolved compiler versions

- Updated the shared Kotlin version from 2.2.10 to 2.4.21.
- Added a catalog alias for `org.jetbrains.kotlin:kotlin-gradle-plugin`, using
  that same version, and a root `buildscript` classpath dependency on it.
  This follows Google's documented mechanism for overriding the KGP runtime
  dependency used by AGP's built-in Kotlin support.
- Kept built-in Kotlin enabled; did not apply `org.jetbrains.kotlin.android`.
- The resolved root build classpath explicitly shows AGP's KGP 2.2.10 dependency
  upgraded to 2.4.21, and Compose compiler Gradle plugin 2.4.21.
- `:app:dependencies --configuration kotlinCompilerClasspath` confirms
  `kotlin-compiler-embeddable:2.4.21`. This is not just a catalog version change.
- Retained KSP 2.3.12, Room 2.8.5, AGP 9.4.1, Gradle 9.8.2, JBR 25,
  compile/target SDK 37 and minimum SDK 26. No application or test source changed.

## Compatibility assessment

Compose compiler and Kotlin compiler are aligned to the same version. KSP's
release history includes Kotlin 2.4-related fixes, and its current processor
compatibility strategy does not require adopting new backing-field features in
existing source. KSP/Room processing and real database tests passed here, so no
KSP update or processor opt-in was introduced.

JetBrains' table lists fully tested KGP 2.4.20-2.4.21 support through Gradle 9.7.0
and AGP 9.3.1, while allowing newer releases with possible limitations. This
project uses newer versions. The result below is project-specific validation,
not a claim that this exact combination is fully vendor-certified. Google's
Kotlin/D8/R8 guidance and successful optimized Release builds were also reviewed.

## Build and schema verification

Fresh `testDebugUnitTest --rerun assembleDebug assembleDebugAndroidTest
assembleRelease bundleRelease lintRelease`, with in-process Kotlin compilation:

- BUILD SUCCESSFUL in 4m 7s; all 146 actionable tasks executed.
- Unit tests: **72 passed**, zero failures/errors/skips.
- KSP, Kotlin compilation, D8/R8, resource shrinking, Compose mapping tasks,
  native-symbol processing, APK and AAB packaging completed.
- No Kotlin compiler warnings/errors were emitted.
- Room schema `app/schemas/com.poetscorner.invoiceqr.data.ScanDatabase/3.json`
  is byte-identical by SHA-256 before/after. No database migration/version change.
- Isolated `.validation` APK/test builds and compiler dependency reports passed
  in 26 seconds.
- Lint: **zero errors, one warning**: the intentionally retained
  `PluralsCandidate` heuristic warning. The Kotlin update notice is gone.
- AGP's known `Configuration.setVisible` Gradle 11 diagnostic and DataStore's
  desktop Java Unsafe warning remain separate; neither was suppressed.

## Android 17 runtime checks

Pixel 10a / API 37 Google Play emulator, emulated rear camera. Only the separate
validation app's data was cleared for instrumentation runs.

### Final confirmation

**46 selected non-language cases passed, zero failures/skips**, in 91.459 seconds.
Coverage includes camera crop/streaming/recreation/zoom, system bars/keyboard,
result restoration, localization, continuous scanning, history/backup settings,
card refresh, engine selection, privacy/supporter preview, seller layout/RTL,
statistics filters/suggestions/sorting/navigation, sharing/clipboard, paging,
real ZXing/ML Kit decoding, database protection and backup round trips/rollback.

### Preliminary failures and comparison

The initial 48-case run had five failures: two already-known app-language
assertions plus SQLite contention, a missing restored result, and an incorrect
continuous-scan count. A focused six-case retry initially retained two restoration
failures. The same six cases passed with saved Kotlin 2.2.10 baseline APKs, and
then passed again with Kotlin 2.4.21 on clean app data. The subsequent complete
46-case non-language confirmation passed without any source/assertion change.
The initial contention remains documented rather than being presented as a
clean first run or as a proven compiler defect.

The two `AppLanguageUiTest` assertions still fail at the same locations:
section ordering (line 95), and Activity identity (line 65). Their failure on
Kotlin 2.2.10 with both Gradle 9.6.0 and 9.8.2 was established in the preceding
Gradle review. They were exercised in the initial 48-case run and tracked
separately from the final 46-case confirmation. They are not reported as passed
or silently disabled in source.

### Optimized Release smoke check

Signed only a copy of the unsigned Release APK with the local debug key for
emulator testing. Cold launch and all five tabs passed; captured UI trees confirm
the expected selected tab in each screenshot. The captured runtime log had no
fatal exception, native fatal signal, native loading or class verification error.
The original Release APK/AAB remain unsigned. No publication or physical-device
installation occurred. Emulator was closed afterward.

Physical camera quality, live billing, older Android runtime versions and IDE
Gradle Sync were not validated by these command-line/API 37 checks. The existing
language-test failures and separately recorded landscape cutout finding remain.

## Evidence

- `logs/build-kotlin-2.4.21-validation.log` and `logs/build-kotlin-2.4.21-runtime-build.log`.
- `build/kotlin-2.4.21/schema-before.json` and `schema-comparison.json`.
- `build/kotlin-2.4.21/device-tests-confirmation.txt` (final 46-case pass).
- Initial run and focused retries/baseline are retained in that directory.
- Release smoke result, runtime log, screenshots and UI trees in that directory.

References:

- https://developer.android.com/build/releases/agp-9-0-0-release-notes#upgrade-to-a-higher-kgp-version
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://kotlinlang.org/docs/compose-compiler-migration-guide.html
- https://kotlinlang.org/docs/gradle-configure-project.html
- https://github.com/google/ksp/releases/tag/2.3.12
- https://developer.android.com/build/kotlin-support

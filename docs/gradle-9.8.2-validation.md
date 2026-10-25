# Gradle 9.8.2 upgrade assessment

Date: 2026-10-25. Current application version: 1.0.5 (8).

Subsequent follow-up: Kotlin/Compose compilers were upgraded to 2.4.21 after
this Gradle-only assessment. See [Kotlin validation](kotlin-2.4.21-validation.md).
Versions and counts below describe the original Gradle upgrade stage.

## Decision and scope

Updated the project Wrapper from 9.6.0 to 9.8.2. The documented compatibility,
successful builds and baseline comparisons support retaining this update.
Updated both README build instructions, which still described the older toolchain.
No application source, tests, dependency versions, signing configuration, SDK
targets or application version were changed by this work.

AGP remains 9.4.1, Kotlin Compose plugin 2.2.10, KSP 2.3.12 and JetBrains Java 25.
The Kotlin 2.4.10 shown by `gradlew --version` is Gradle's embedded Kotlin for
build logic; it does not mean the application Compose compiler plugin was updated.

## Compatibility and integrity

- Gradle's current 9.8.2 compatibility matrix lists tested AGP versions from
  9.0 through 9.5.0-alpha02, including this project's AGP line. Java 25 is supported.
- AGP 9.4 lists Gradle 9.6.0 as its minimum/default version.
- Reviewed the 9.x migration notes, including Kotlin DSL changes. Project build
  logic compiles and the custom native-symbol task runs successfully.
- Gradle's release notes recommend 9.8.2 and document security and dependency
  resolution fixes included in this release line.
- Regenerated the launch scripts and Wrapper JAR through the Wrapper task,
  including a second invocation under Gradle 9.8.2.
- Distribution SHA-256 matches the official checksum and is pinned in properties:
  `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`.
- Regenerated Wrapper JAR SHA-256 matches the official checksum:
  `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`.
- Download transport was slow; completed the same official distribution through
  the official download mirror using a resumed curl transfer, verified it, and
  seeded the Wrapper cache. The configured distribution URL remains the standard
  `services.gradle.org` URL.

## Fresh build results

Using the new Wrapper and JBR 25.0.3:

```text
testDebugUnitTest --rerun assembleDebug assembleDebugAndroidTest assembleRelease bundleRelease lintRelease --warning-mode all
```

- BUILD SUCCESSFUL in 4m 5s; all 143 actionable tasks executed.
- Unit tests: 72 passed, zero failures, errors or skips.
- KSP/Room processing, R8 minification, resource shrinking, APK/AAB packaging
  and the custom native-symbol pipeline completed.
- Release APK badging: version 1.0.5 (8), minimum 26, compile/target 37.
- AAB contains 20 JNI binaries and no release signing certificates. Its three
  AGP-produced DataStore symbol metadata entries match the packaged ELF build
  IDs, but lack `.symtab`/`.debug_info`; they do not establish full symbol coverage.
- Release outputs remain unsigned. No publication or physical-device installation.
- Separate `.validation` Debug/test APK builds passed in 1m 2s.
- Lint: **zero errors, two warnings**. The Gradle update notice is gone.
  Remaining: Kotlin Compose plugin 2.2.10 -> 2.4.21 and the previously retained
  plural heuristic warning.

## New build diagnostic

Gradle 9.8.2 reports deprecated `Configuration.setVisible(boolean)` use, scheduled
for removal in Gradle 11. A deprecation trace identifies AGP's
`BasePlugin$Companion.createAndroidJdkImageConfiguration` (`BasePlugin.kt:362`).
This is an AGP implementation call, not project build logic. It does not fail
the current build and was not suppressed. Track it with a future stable AGP
update; no prerelease AGP was introduced to remove a warning.

The existing DataStore protobuf Unsafe warning during desktop JVM tests remains.

## Runtime checks and baseline comparison

On Pixel 10a / Android 17 API 37:

- Nine focused checks passed: camera crop/guide, streaming after recreation,
  zoom, system bars/theme/keyboard, Google services and real ZXing/ML Kit decoding.
- Two `AppLanguageUiTest` cases failed: section position ordering at line 95,
  and the same-Activity assertion for matching device language at line 65.
- Rebuilt the exact same source with the installed Gradle 9.6.0 directly,
  without reverting the project's new Wrapper. Both language tests reproduced
  the same failures at the same assertions on the old version.
- Compared all ZIP entry payloads of the isolated Debug APK (591 entries) and
  test APK (54 entries), built by the two Gradle versions: **zero differences**.
  DEX, resources, manifest, native libraries and test code are identical.
- These failures are pre-existing under this source/device configuration, not
  newly introduced by Gradle 9.8.2. They remain recorded for separate diagnosis;
  no application/test behavior or assertion was changed to make them pass.
- Restored the saved Gradle 9.8.2 validation APKs after baseline comparison.
- Signed only a copy of the optimized 9.8.2 Release APK with the local debug key
  for emulator testing. Cold launch and all five navigation tabs passed. The
  captured log contained no fatal exception, native fatal signal, native loading
  error or class verification error.
- Only the separate validation app's data was reset for instrumentation checks;
  no normal-app database was cleared. Emulator closed after verification.

Android Studio was not running, so IDE Gradle Sync was not invoked or certified
by this command-line validation. Physical camera quality and live billing were
outside this tooling check.

## Evidence

- `build-gradle-9.8.2-version.log`, Wrapper stage logs and
  `build-gradle-9.8.2-validation.log`.
- `build-gradle-9.8.2-runtime-build.log` includes the deprecation stack trace.
- `build-gradle-9.6.0-language-baseline.log`.
- `build/gradle-9.8.2/device-tests.txt` and `language-tests-9.6.0.txt`.
- `runtime-entry-comparison.json` and `all-entry-comparison.json` in that folder.
- Release smoke logs, results, UI XML and screenshots in that folder.

References:

- https://docs.gradle.org/9.8.2/release-notes.html
- https://docs.gradle.org/current/userguide/compatibility.html
- https://docs.gradle.org/current/userguide/upgrading_version_9.html
- https://developer.android.com/build/releases/agp-9-4-0-release-notes
- https://developer.android.com/studio/releases
- https://services.gradle.org/distributions/gradle-9.8.2-bin.zip.sha256
- https://services.gradle.org/distributions/gradle-9.8.2-wrapper.jar.sha256

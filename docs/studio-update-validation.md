# Android Studio update validation

Date: 2026-10-24. App version: 1.0.4 (7).

## Observed configuration

- Running Android Studio 2026.2.1, build 262.9437.185.2621.16467767,
  installed under `C:\Program Files\Android\Android Studio1`.
- Project catalog now specifies AGP 9.4.1; wrapper specifies Gradle 9.6.0.
  These differ from the earlier validated AGP 9.2.1 / Gradle 9.4.1 setup.
  No versions were changed during this inspection.
- Wrapper reports launcher JBR 25.0.3. Daemon criteria select JetBrains Java 25.
- Compile SDK remains 37, target SDK 36, minimum SDK 26.
- IDE log records successful sync at 10:37:10, taking approximately four seconds.

## Fresh verification

Using the project's wrapper and the new IDE's bundled JBR:

```text
gradlew.bat testDebugUnitTest lintRelease assembleDebug assembleRelease -Pkotlin.compiler.execution.strategy=in-process --console=plain
```

- BUILD SUCCESSFUL in 4m 44s; 102 tasks executed.
- Unit tests: 72 passed, zero failures, errors, or skips.
- Debug APK and unsigned Release APK generated successfully, including R8.
- Lint: zero errors, 11 warnings (previous toolchain report: 12).
  Remaining categories: seven library notices, one Gradle update notice,
  one Kotlin Compose plugin update notice, one target SDK notice, and the
  previously retained plural warning. The AGP update notice is gone.
- DataStore's protobuf dependency emitted a `sun.misc.Unsafe::arrayBaseOffset`
  deprecation warning on Java 25 during unit tests; tests still passed.
- Native-library stripping emitted the same five-library warning recorded in
  the previous toolchain build. Libraries were packaged as supplied.

Local log: `logs/build-studio-update-check.log`. Lint reports are under
`app/build/reports/lint-results-release.*`.

This check did not run emulator/device tests, build a bundle, or validate native
symbol coverage. Earlier device-test results belong to the preceding toolchain.
The previously recorded landscape cutout issue remains outside this inspection.

AGP 9.4 requires Gradle 9.6.0 according to the official compatibility table:
https://developer.android.com/build/releases/agp-9-4-0-release-notes

## Warning investigation

The DataStore warning identifies
`androidx.datastore.preferences.protobuf.UnsafeUtil` in
`datastore-preferences-external-protobuf:1.3.0`, not application code. It appears
while running JVM unit tests with Java 25. JDK 24 and later warn on the first use
of these deprecated Unsafe memory-access methods. This observed warning is from
the desktop test runtime; it does not demonstrate a failure on Android. The
DataStore release page lists 1.3.0 as the current stable version. No dependency
override or JVM warning suppression was added.

Detailed native investigation command:

```text
gradlew.bat :app:stripReleaseDebugSymbols --rerun-tasks --info --console=plain
```

It succeeded in five seconds (three tasks), but explicitly reported missing
strip tools for all four ABIs. AGP searches for NDK 28.2.13676358, which is not
installed. The existing `ndk/27.1.12297006` folder contains only `.installer`, not
a usable NDK. Installing the required NDK supplies the missing tools; it was not
installed during this investigation.

This missing-tool warning is distinct from absent publisher debug symbols.
A fresh ELF section check confirmed all 20 merged native binaries lack both
`.symtab` and `.debug_info`. Installing an NDK cannot recover that information;
matching symbols or unstripped libraries must come from the publishers.

Evidence log: `logs/build-native-warning-investigation.log`.

### NDK installation follow-up

The user subsequently installed NDK 28.2.13676358. Its `source.properties` and
Windows `llvm-strip.exe` are now present. Reran both
`:app:stripDebugDebugSymbols` and `:app:stripReleaseDebugSymbols` with
`--rerun-tasks --info`: BUILD SUCCESSFUL in five seconds, six tasks executed.
The log confirms the installed NDK's strip tool ran successfully and contains
no `Unable to strip` or `missing strip tool` messages. No project configuration
change was needed. This resolves the missing-tool warning, not the separate
publisher symbol availability or DataStore Java warning.

Evidence log: `logs/build-ndk-install-check.log`.

References:

- https://docs.oracle.com/en/java/javase/26/migrate/migrating-from-jdk-8-later-jdk-releases.html
- https://developer.android.com/jetpack/androidx/releases/datastore
- https://developer.android.com/studio/projects/install-ndk
- https://developer.android.com/build/include-native-symbols

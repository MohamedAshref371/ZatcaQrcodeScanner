# Compose test v2 migration validation

Date: 2026-10-24. App version: 1.0.4 (7).

## Change

Migrated all 14 Compose JUnit rule imports to
`androidx.compose.ui.test.junit4.v2`:

- Nine `createAndroidComposeRule` imports.
- Five `createComposeRule` imports.

The rules now use their default StandardTestDispatcher. No dispatcher override,
warning suppression, assertion removal, timeout extension or new sleep was
introduced. Existing synchronization and assertions worked with v2 unchanged.
Production code, dependency versions and SDK configuration were not modified.

## Verification

- Forced `compileDebugAndroidTestKotlin --rerun`, assembled the instrumentation
  APK and ran `lintRelease`: BUILD SUCCESSFUL in 32 seconds.
- Built separate `.validation` Debug/instrumentation APKs: passed in 1m 43s.
- Both compilation logs contain **zero Kotlin compiler warnings or errors**;
  all 14 deprecated-rule warnings are gone.
- Source search found no remaining legacy rule imports, direct legacy test
  environment APIs, or UnconfinedTestDispatcher overrides.
- Pixel 10a/API 37, Google Play x86_64, emulated rear camera:
  **37 passed, zero failed, zero skipped**, 84.916 seconds.
- The suite includes every migrated class (26 test cases), plus result
  restoration, localization and real ZXing/ML Kit integration checks.
- Coverage includes camera recreation/zoom, theme/insets/keyboard, continuous
  scanning, card refresh, backup/history navigation, engine selection, privacy,
  supporter preview, seller layout/RTL, statistics suggestions/filters/sorting,
  paging, and clipboard/sharing with return from the system chooser.
- Verified the emulator had no blocking System UI dialog before tests. Stopped
  the Gradle daemon before runtime checks to release host memory. Closed the
  emulator after verification.
- Only the separate validation application's data was reset; the normal
  emulator application was preserved. No physical-device installation/upload.
- Lint remains **zero errors and three warnings** (Gradle update, Kotlin Compose
  compiler plugin update and the previously retained plural heuristic).

Validation was focused on the changed instrumentation tests. Production Release
builds, unit tests and device checks from the preceding Compose update remain
recorded in that report; they were not unnecessarily rerun for import-only edits.

Logs: `build-compose-test-v2.log`, `build-compose-test-v2-isolated.log`,
`build/compose-test-v2/device-tests.txt`.

Reference: https://developer.android.com/develop/ui/compose/testing/migrate-v2

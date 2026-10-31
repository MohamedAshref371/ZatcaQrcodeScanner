# Core and Lifecycle update validation

Date: 2026-10-24. App version: 1.0.4 (7).

## Changes

- Updated Core KTX from 1.18.0 to 1.19.1 first. Debug, Release, instrumentation
  APK compilation, fresh unit tests and lint passed. Lint dropped from ten to
  nine warnings. Log: `logs/build-core-1.19.1-validation.log`.
- Updated the shared Lifecycle version from 2.10.0 to 2.11.0 for runtime KTX,
  runtime Compose, ViewModel Compose and ViewModel SavedState.
- The new resolved dependencies enabled Compose lint checks that found five
  resource lookups through `LocalContext` in continuous-scanning snackbars.
  Replaced these with `LocalResources` and added resources to the existing
  configuration/lifecycle effect keys so resource changes restart collection.
  No baseline, lint suppression, or warning-disabling JVM flags were added.
- Target/compile SDK remain 37, minimum SDK remains 26. AGP, Gradle, Kotlin
  plugin versions, Compose BOM and Foundation declaration versions are unchanged.

## Resolved dependency impact

Lifecycle's transitive dependencies select Compose UI/Runtime 1.11.0,
Foundation 1.10.0 and Material 3 1.3.1. The declared Foundation 1.9.2 is a
minimum dependency request, not a strict version pin. The existing old BOM
still needs its separately planned coordinated upgrade.

Resolution evidence: `logs/build-core-lifecycle-dependencies.log`, generated with
`:app:dependencies --configuration debugRuntimeClasspath`.

## Verification

- Final `testDebugUnitTest --rerun assembleDebug assembleRelease lintRelease`:
  BUILD SUCCESSFUL in 3m 25s, 21 tasks executed and 82 up-to-date.
- Fresh unit tests: 72 passed, zero failures, errors or skips. The previously
  documented DataStore protobuf Unsafe warning remains on desktop Java 25.
- Instrumentation APK compiled with the new dependencies before the snackbar
  resource fix; the final Debug app containing the fix was installed with that
  test APK on the existing isolated EdgeValidation API 36.1 emulator, port 5580.
- Device results: 17 passed, zero failures, one skipped (camera zoom unsupported
  by this emulator's current virtual-scene camera).
- Covered preview streaming after activity recreation, system bar/keyboard
  bounds, system icon themes, SavedStateHandle result restoration and navigation,
  deleted-record behavior, Arabic/English resource checks, saved filters, batch
  scanning persistence, and the continuous-scanning snackbar action and lifecycle.
- Emulator closed after testing. No physical-device install or Play upload.
- Release APK is unsigned. No new bundle was generated.
- Android 17/API 37 runtime testing remains outstanding; these emulator results
  cover API 36.1. The previously recorded horizontal cutout issue is unchanged.

Final lint: **zero errors, five warnings**:

| Category | Count |
| --- | ---: |
| Gradle update | 1 |
| Kotlin Compose compiler plugin update | 1 |
| Compose BOM update | 1 |
| Foundation keyboard/mouse support recommendation | 1 |
| Previously retained plural heuristic warning | 1 |

Logs: `logs/build-lifecycle-2.11.0-validation.log` (initial lint failure),
`logs/build-core-lifecycle-final.log`, `build/core-lifecycle-device-tests.txt`.

References:

- https://developer.android.com/jetpack/androidx/releases/core
- https://developer.android.com/jetpack/androidx/releases/lifecycle

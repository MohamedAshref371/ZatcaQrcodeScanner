# Lint cleanup validation

Date: 2026-10-23. App version: 1.0.4 (7).

Current status after Compose BOM 2026.09.00: zero lint errors and three warnings
(Gradle update, Kotlin Compose plugin update, retained plural heuristic).
Foundation now follows the BOM. Unit tests: 72 passed. Final API 37 interface
suite: 37 passed, no failures/skips. Optimized Release launch/navigation passed.
Instrumentation compilation separately emits 14 legacy Compose JUnit rule API
deprecation warnings; these are documented, not suppressed, for a v2 test-harness
migration. See [Compose update validation](compose-update-validation.md).
The entries below describe earlier stages.

Android 17 follow-up: selected API 37 runtime suite passed all 42 tests, with no
failures/skips; optimized Release launch and five-tab checks passed. Old API 36.1
AVDs/image were removed after verification, as requested. See
[Android 17 runtime validation](android17-runtime-validation.md).

Latest status after Core 1.19.1 and Lifecycle 2.11.0: zero lint errors and five
warnings (four library/tooling notices and the retained plural warning). Fresh
unit tests: 72 passed. Debug/Release builds passed. API 36.1 emulator checks:
17 passed, one skipped because camera zoom is unsupported. The update also
required fixing five Compose resource-access lint errors in continuous-scan
snackbars. See [Core/Lifecycle validation](core-lifecycle-update-validation.md).

Current status after the requested target SDK 37 update: zero lint errors and
10 warnings (nine library/tooling notices and the retained plural warning).
Debug, Release and instrumentation APK builds passed. Fresh unit tests passed.
Android 17 device validation remains outstanding. See
[Target SDK 37 validation](target-sdk-37-validation.md).
The follow-ups below describe earlier stages.

Latest follow-up on 2026-10-24: Android Studio 2026.2.1 is installed and IDE
sync succeeds. The project now uses AGP 9.4.1 / Gradle 9.6.0 / JetBrains Java 25.
Fresh unit tests (72), Debug/Release builds, and lint passed; lint now reports
0 errors and 11 warnings. See [Studio update validation](studio-update-validation.md).
The following paragraph records the preceding toolchain validation.

Follow-up on 2026-10-24: Play Services Base was updated to 18.11.1, DataStore
to 1.3.0, and CameraX to 1.6.2, with validation. The subsequent AGP 9.2.1 / Gradle
9.4.1 / compile SDK 37 upgrade passed command-line validation. Current lint has
0 errors and 12 warnings: ten update notices, the intentional target SDK 36
notice, and the retained plural warning. See
[Play services update validation](play-services-update-validation.md).
See also [DataStore update validation](datastore-update-validation.md).
The [CameraX validation and Core compatibility review](camera-update-validation.md)
records the SDK/toolchain requirements preventing an isolated Core 1.19.1 update.
The [toolchain upgrade report](toolchain-upgrade-validation.md) records the new
build requirements and the installed Android Studio's required update for IDE sync.
The counts below describe the original cleanup before that follow-up.

## Changes

- Centralized all 17 directly declared library dependencies in
  `gradle/libs.versions.toml`, sharing CameraX, Lifecycle, and Room versions.
  The existing KSP plugin version is also declared through the catalog.
- Updated Room runtime, KTX, and compiler together from 2.8.4 to 2.8.5.
  Its release notes describe explicit failure for queries/invalidation operations
  attempted after closing the database. The database schema is unchanged.
- Updated ExifInterface from 1.4.1 to 1.4.2, which fixes parsing of JPEGs with
  permitted extra 0xFF bytes before markers.
- Replaced four URI conversions with `String.toUri()` and two preference editor
  chains with KTX `edit`, preserving asynchronous `apply()` behavior.
- Made `modifier` the first optional parameter in `StatisticsCard`. Its callers
  already name optional arguments.
- Removed seven unused XML colors and six unused strings from both English and
  Arabic resources. Searches found no callers or dynamic resource lookups in
  the app source, tests, tools, or publishing files examined.

## Lint results

Fresh `lintRelease`: **0 errors, 15 warnings**, down from 56 warnings.

| Category | Before | After |
| --- | ---: | ---: |
| UseTomlInstead | 17 | 0 |
| UnusedResources | 13 | 0 |
| UseKtx | 6 | 0 |
| ModifierParameter | 1 | 0 |
| GradleDependency | 16 | 12 |
| AndroidGradlePluginVersion | 1 | 1 |
| NewerVersionAvailable | 1 | 1 |
| PluralsCandidate | 1 | 1 |

No lint checks were disabled and no baseline or suppressions were added.
The plural warning is intentionally unchanged, as requested: `%1$d` identifies
a tag rather than a count requiring plural forms.

## Deferred upgrades

The remaining 14 dependency/tooling notices are retained for a coordinated
upgrade: AGP, Kotlin Compose compiler plugin, Core, Lifecycle (four artifacts),
Compose BOM/Foundation, CameraX (three artifacts), Play Services Base, and
DataStore. A newer version notice alone does not establish a defect in the
current release. This cleanup selected the two small patch updates above and
does not claim the remaining versions are the latest.

The resolved baseline uses Compose UI/Foundation 1.9.2 and Material 3 1.3.0.
The Foundation pin was retained: changing the BOM can change several UI and test
libraries together, and should receive its own compatibility and UI review.

## Verification

- `testDebugUnitTest`: 72 tests, zero failures/errors/skips.
- `lintRelease`, `assembleDebug`, and `assembleDebugAndroidTest`: passed.
- Localization audit: 307 translated keys; matching argument formats and no
  missing source references.
- Device checks use the separate `.validation` application on an API 36.1
  emulator. They cover Room storage, reopening/migration protection, backup
  import/rollback, saved statistics filters, privacy-link intent handling, card
  refresh, and supporter preview/preferences.
- Selected device tests: 19 passed, zero failures.
- `assembleRelease`: passed, including R8 minification and resource shrinking.
  The generated APK is unsigned. No release upload or installation on the user's
  normal application was performed.

This work does not fix the separately recorded landscape cutout issue in
`edge-to-edge-review-1.0.4.md` and does not establish Play Console warning removal.

References:

- https://developer.android.com/jetpack/androidx/releases/room#2.8.5
- https://developer.android.com/jetpack/androidx/releases/exifinterface#1.4.2
- https://developer.android.com/develop/ui/compose/bom

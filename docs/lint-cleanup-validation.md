# Lint cleanup validation

Date: 2026-10-23. App version: 1.0.4 (7).

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

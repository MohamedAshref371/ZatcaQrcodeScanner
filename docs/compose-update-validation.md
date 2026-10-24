# Compose update validation

Date: 2026-10-24. App version: 1.0.4 (7).

## Change and resolved versions

Updated Compose BOM from 2024.09.00 to 2026.09.00. Removed the separate Foundation
1.9.2 version declaration so Foundation follows the same BOM as the app and
instrumentation Compose dependencies.

`debugRuntimeClasspath` confirms:

| Component | Before | After |
| --- | --- | --- |
| Compose UI / Runtime | 1.11.0 | 1.12.1 |
| Foundation | 1.10.0 | 1.12.1 |
| Material 3 | 1.3.1 | 1.4.0 |

Migrated three statistics tooltip position providers to
`rememberTooltipPositionProvider(TooltipAnchorPosition.Above)`, retaining the
previous placement preference. Replaced the deprecated `MenuAnchorType` alias
with `ExposedDropdownMenuAnchorType.PrimaryEditable`.

Kotlin Compose compiler plugin, KSP, AGP, Gradle and SDK versions are unchanged.
No lint baseline or suppression was introduced.

## Build and lint

- Initial full build passed in 6m 24s (136 tasks executed).
- After migrating the four production API calls, fresh unit tests, Debug,
  Release, instrumentation APK builds and lint passed in 3m 3s.
- Unit tests: 72 passed, zero failures/errors/skips.
- No production Kotlin deprecation warning was emitted by the final build.
- Final instrumentation APK rebuilt successfully after the share test adjustment.
- Lint: **zero errors, three warnings**, down from five. Remaining notices:
  Gradle 9.6.0 -> 9.8.2, Kotlin Compose plugin 2.2.10 -> 2.4.21, and the
  previously retained plural heuristic warning.

Separate compiler warnings: instrumentation compilation emitted **14 warnings**
for legacy Compose JUnit rule APIs recommending `androidx.compose.ui.test.junit4.v2`.
These are not lint issues or production API calls. They are recorded rather than
suppressed. Migration changes test coroutine scheduling from
UnconfinedTestDispatcher to StandardTestDispatcher and should be handled as a
separate test-harness migration with its own synchronization review. Existing
rules still compile and the selected suite passes.

The known DataStore protobuf Unsafe warning on desktop Java 25 remains separate.

## Android 17 runtime verification

Pixel 10a/API 37 Google Play emulator, port 5582, emulated rear camera.

Final complete suite: **37 passed, zero failures, zero skips**, in 84.439 seconds.
Covered camera streaming/recreation/zoom, system bars/theme and keyboard bounds,
result restoration, localization, continuous scanning, card refresh, history and
backup settings navigation, engine selection, supporter preview/privacy,
seller-name layout/RTL, statistics filtering/autocomplete/sorting/navigation,
VAT clipboard/share actions, history paging, and real ZXing/ML Kit decoding.

### Investigation of failed preliminary runs

The first run had five failures; resetting test app data and rerunning reduced
these to three platform-interaction failures. A real-tap probe then found a
blocking Android **System UI isn't responding** dialog. The saved emulator event
log records that dialog taking input focus before the test suite began.

Stopped the idle Gradle daemon to release host memory and cold restarted the
emulator; confirmed the dialog was absent. Keyboard and clipboard interactions
then passed without application or test changes. A remaining share return test
consistently sent Back after the app paused but before the system chooser was
ready. Added explicit synchronization on the system chooser accessibility window
and idle state before Back, retaining the assertion that the app resumes.
The isolated share test passed, followed by the complete 37-test passing run.
No test assertion was removed and no test was skipped.

### Optimized Release smoke check

Signed only a copy of the final unsigned Release APK with the standard local
debug key for this emulator. Cold launch returned `Status: ok`; all five tabs
rendered and were captured as UI XML/screenshots. Inspected scanner/statistics
screenshots. Captured log had no fatal exception, native fatal signal, native
loading or class verification errors. Original Release APK remains unsigned.

Emulator closed afterward. No physical-device install or Play upload occurred.
These checks do not establish physical camera quality or repair the previously
recorded landscape cutout overlap.

## Evidence

- `build-compose-update.log`, `build-compose-final.log`,
  `build-compose-share-test.log`.
- `build/compose-update/dependencies.txt`.
- `build/compose-update/device-tests-verified.txt` (final passing suite).
- Earlier failed runs, share synchronization retry, System UI event log and
  UI dump are retained under `build/compose-update` for diagnosis.
- `build/compose-update/release-results.json`, `release-logcat.txt`,
  `release-smoke-result.log` and `release-*.png`.

References:

- https://developer.android.com/develop/ui/compose/bom
- https://developer.android.com/develop/ui/compose/bom/bom-mapping
- https://developer.android.com/reference/kotlin/androidx/compose/material3/TooltipDefaults

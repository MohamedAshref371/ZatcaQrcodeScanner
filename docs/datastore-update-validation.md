# DataStore update validation

Date: 2026-10-24. App version: 1.0.4 (7).

## Change and scope

Updated `datastore` in `gradle/libs.versions.toml` from 1.2.1 to 1.3.0.
The existing Preferences DataStore delegate, filename, keys, and application
settings repository remain unchanged. No switch to the new Builder API,
encryption, or a new storage format was made by application code.

The stored settings are scan processing options, resolution, speed, torch,
tap focus, theme, golden-theme preference, and preferred scanner engine.
Language follows Android's locale configuration rather than this DataStore.
Arabic/English resources are checked separately.

## Upgrade protocol

Testing uses only `com.poetscorner.invoiceqr.validation` on the separate API 36.1
emulator, preserving the normal application. Original validation settings were
backed up before seeding.

`DataStoreUpgradeTest` has explicit stages selected using instrumentation argument
`settingsUpgradeStage`. Outside the selected stage, tests skip to avoid accidental
settings changes during ordinary device suites.

1. Build and install with DataStore 1.2.1. Run
   `seedWithOldLibrary` using `-e settingsUpgradeStage seed`: writes nondefault
   settings through the production repository and checks the full settings object.
2. Stop the application, update the library, build, and install with `adb install -r`
   without clearing data. Run `readOldSettingsAndSaveNewChoices` with stage
   `upgrade`: checks the entire old object, then saves a different theme, engine,
   resolution, speed, and processing configuration.
3. Force-stop the application again. Run `readNewChoicesAfterProcessRestart` with
   stage `restart`: checks the updated object from a new application process.

All three stages passed, each in a separate application process. The upgrade
stage successfully read the full settings object written by 1.2.1 before saving
new choices. The restart stage recovered the new choices from disk.

## Results

- `testDebugUnitTest`: 72 passed, zero failures/errors/skips.
- `lintRelease`, `assembleDebug`, `assembleDebugAndroidTest`: passed using the
  existing SDK and toolchain settings.
- `lintRelease`: 0 errors, 13 warnings (12 update notices and the intentionally
  retained plural warning). No lint suppressions were added.
- Device checks: three explicit upgrade stages plus eight UI/localization tests
  passed. Covered light/dark system icon appearance, keyboard reachability,
  Arabic/English resource resolution, supporter preview/preferences across
  activity recreation, and continuous scanner UI lifecycle behavior.
- The original validation settings were restored after stopping the app, and
  the restored file was verified byte-for-byte against its backup.
- `assembleRelease`: passed with R8 minification and resource shrinking. The
  generated APK is unsigned. No Play Console upload or installation on the
  normal user application was performed.

## Logs and references

- `build-datastore-baseline.log`
- `build-datastore-update.log`
- `build/datastore-upgrade/` (generated local logs and settings backup)
- https://developer.android.com/jetpack/androidx/releases/datastore#1.3.0

## Limits

This checks the application's Preferences DataStore use. It does not validate
unused Guava, encrypted, web, or multiprocess DataStore APIs. It does not address
the separately reported landscape cutout defect or Play Console edge-to-edge
warnings. Device checks are on an emulator; older Android versions and physical
devices remain outside this validation.

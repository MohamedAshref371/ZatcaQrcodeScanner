# Play services update validation

Date: 2026-10-24. App version: 1.0.4 (7).

## Selected update

Updated `playServicesBase` in `gradle/libs.versions.toml` from 18.5.0 to 18.11.1.
The resolved dependencies also update Basement from 18.9.0 to 18.12.0 and Tasks
from 18.2.0 to 18.4.1. No application logic or SDK/toolchain settings changed.

Google's release notes describe the 18.11.1 Basement dependency update as a fix
for runtime compatibility issues. The new minimum API level is 24, below this
app's existing minimum of 26. The removed legacy Google Sign-In APIs are not used
by the app source examined.

Lifecycle was considered first but left unchanged: its 2.11.0 release notes
require AGP 9.2.0+ for Compose artifacts, while this project uses AGP 9.0.1.
That update belongs with the SDK/toolchain work rather than an isolated library
change.

## Verification

- `testDebugUnitTest`: 72 tests passed, zero failures/errors/skips, including
  the module controller's fake-installer success/failure/cancellation cases.
- `lintRelease`, `assembleDebug`, and `assembleDebugAndroidTest`: passed.
- `lintRelease`: 0 errors, 14 warnings (previously 15). The Play Services Base
  update notice is gone. No lint suppressions were added.
- First device run: six tests passed on the separate `.validation` application
  on an API 36.1 Google Play emulator. Covered the real module-availability
  query, engine selection/retry UI, continuous scanning and its UI lifecycle,
  and malformed-QR rejection.
- The real `GoogleMlKitModuleInstaller.available()` call completed successfully
  and reported the barcode module already installed.
- Added a second integration check using a fixed QR bitmap and the production
  ML Kit decoder. This check requires an installed module and explicitly skips
  on devices where it is absent; it does not initiate a download.
- Final device rerun: seven tests passed, zero failures or skips. Both real
  integration checks passed, including decoding the known QR bitmap through the
  production `MlKitDecoder`.
- `assembleRelease`: passed with R8 minification and resource shrinking.
  The generated release APK is unsigned; no Play Console upload was performed.

Logs: `logs/build-play-services-update.log`, `logs/build-play-services-release.log`, and
`build/edge-review/play-services-device-tests.txt` and
`build/edge-review/play-services-final-device-tests.txt` (local generated files).

## Limits

The live query and bitmap decoding exercise real Google Play services rather
than a fake client. A fresh module download, missing/outdated device services,
Android API 26 hardware, and physical camera recognition have not been validated
by these integration checks. They do not establish removal of the separate Play
Console edge-to-edge warnings or fix the recorded landscape cutout issue.

References:

- https://developers.google.com/android/guides/releases#october_14_2026
- https://developers.google.com/android/guides/releases#september_17_2026
- https://developer.android.com/jetpack/androidx/releases/lifecycle#2.11.0

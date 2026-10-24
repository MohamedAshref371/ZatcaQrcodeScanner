# Android 17 runtime validation and Android 16 image cleanup

Date: 2026-10-24. App version: 1.0.4 (7).

## Device and build

- User-created Pixel 10a AVD, Google Play x86_64 Android 17 image.
- Runtime verified with `ro.build.version.sdk=37` and release `17`.
- Started hidden on emulator-5582, cold boot, software rendering, emulated rear
  camera. Closed after testing; the Pixel 10a AVD and API 37 image remain installed.
- Separate `.validation` Debug application built with target/compile SDK 37 and
  minimum SDK 26. Debug and instrumentation builds passed.
- Added a real-bitmap ZXing decoding integration test using the existing QR asset;
  this exercises the production native decoder on API 37.
- Initial instrumentation launch was rejected because the two APKs used different
  local debug keystores. Signed a copy of the test APK with the same project-local
  debug key as the app, verified matching certificate SHA-256 digests and retried.
  No production signing keys or app signing configuration were changed.

## Runtime results

**42 passed, zero failed, zero skipped**, 152.15 seconds.

Covered real camera preview after activity recreation and actual zoom state,
system bars/theme/keyboard bounds, real ZXing and ML Kit bitmap decoding and
Google Play services availability, database protection and reopening, backup
round trips/rollback, saved filters, result restoration/back navigation, deleted
records, Arabic/English resource behavior, continuous-scanning state/snackbar,
exclusive data transfer, invoice sharing content, scan-engine selection UI,
card refresh, backup settings, and history navigation/export availability.

## Optimized Release smoke check

Copied the unsigned Release APK under `build/android17-validation`, signed that
copy with the standard local debug key and installed it only on the API 37
emulator. The original Release output remains unsigned. Cold launch returned
`Status: ok`; all five tabs rendered. UI XML and screenshots were saved for each
tab. Inspected the scanner screenshot. Captured log had no fatal exception,
native fatal signal, UnsatisfiedLinkError, NoClassDefFoundError or VerifyError.

These are emulator checks. Physical camera quality/focus/flash, live store billing,
full performance profiling and every device configuration are not covered.
The previously recorded landscape cutout overlap was not repaired or retested
with an injected tall cutout in this run. No Play upload or Console reanalysis.

## Authorized cleanup

After the API 37 tests and Release checks passed, removed the following API 36.1
AVDs and their registrations, after checking their configs and absolute paths:

- User AVD `Medium_Phone` (registration `Medium_Phone_API_36.1.ini`).
- Generated project AVD `EdgeValidation`.
- Generated project AVD `InvoiceStoreCapture`.
- SDK system-image directory `system-images/android-36.1` (Google Play x86_64).

Verified all removed paths are absent. Pixel_10a and the Android 37.0 system image
remain. SDK platforms, Build Tools, NDK and Emulator tools were preserved.
After cleanup, `assembleRelease lintRelease` passed in 56 seconds: zero lint
errors and five warnings remain. Log: `build-after-android36-removal.log`.
Historical API 36.1 test reports remain valid as records; rerunning them now
requires reinstalling that system image and recreating an AVD.

Local evidence: `build-android17-validation.log`,
`build-android17-native-test.log`, `build/android17-validation/device-tests.txt`,
`release-results.json`, `release-logcat.txt`, `release-*.png` and
`deleted-android36-paths.txt` in that same directory.

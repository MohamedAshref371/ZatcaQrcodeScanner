# Target SDK 37 validation

Date: 2026-10-24. App version: 1.0.4 (7).

Follow-up: Android 17/API 37 runtime validation is now complete for the selected
suite: 42 passed, zero failed/skipped, plus optimized Release launch/navigation
checks. Android 36.1 AVDs/image were then removed at the user's request. See
[Android 17 runtime validation](android17-runtime-validation.md).
The remaining text records the original target-change validation stage.

## Change

Raised `targetSdk` from 36 to 37 at the user's request. Compile SDK remains 37
and minimum SDK remains 26. Updated both README build instructions. No library
or tooling versions were changed in this step.

Reviewed Android 17 target-dependent and all-app behavior changes. Searches in
application source found no direct MessageQueue private reflection, static-final
reflection, System.load calls, PendingIntent/IntentSender usage, or widget code.
The manifest declares no contacts or SMS access. This source review does not
establish that dependencies are unaffected. Native QR decoding, Google services,
camera recreation, keyboard behavior, and lifecycle flows need Android 17 runtime
validation. The existing landscape cutout issue remains separately recorded.

## Verification

- `testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest
  lintRelease`: BUILD SUCCESSFUL in 1m 25s; 53 tasks executed, 83 up-to-date.
- Forced a separate fresh `:app:testDebugUnitTest --rerun`: passed, with the
  previously explained DataStore protobuf Unsafe warning on desktop Java 25.
- Lint: zero errors, 10 warnings. `OldTargetApi` is gone. Nine library/tooling
  update notices and the retained plural warning remain.
- `aapt2 dump badging` on the unsigned Release APK confirms target 37, compile
  37 and minimum 26.
- Android instrumentation APK compiles; instrumentation tests were not run.
  Only an API 36.1 system image is installed locally; API 37 runtime validation
  remains necessary before publishing. No device installation or upload occurred.

Logs: `logs/build-target37-validation.log`, `logs/build-target37-unit-tests.log`.

References:

- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all

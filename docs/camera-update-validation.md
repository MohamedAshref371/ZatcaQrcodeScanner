# CameraX update validation and Core compatibility review

Date: 2026-10-24. App version: 1.0.4 (7).

## Core compatibility finding

An attempted Core/Core KTX update from 1.18.0 to 1.19.1 failed the AAR metadata
check: both artifacts require compile SDK 37+ and AGP 9.1.0+. This project uses
compile SDK 36 and AGP 9.0.1. The Core version was restored to 1.18.0; no SDK,
Gradle wrapper, or AGP setting was changed.

The official 1.19.1 source archive's `WindowCompat.enableEdgeToEdge` still calls
`setStatusBarColor` and `setNavigationBarColor`. A Core update alone therefore
does not establish removal of the Play Console deprecated API warning. This is
a source inspection finding, not a new inspection of the uploaded release DEX.

Evidence: `logs/build-core-update.log` and
`build/core-update/WindowCompat-enableEdgeToEdge.java.txt` (generated files).

## Selected update

Updated the shared CameraX version from 1.5.0 to 1.6.2 for `camera-camera2`,
`camera-lifecycle`, and `camera-view`. The resolved graph also selects CameraX
core and camera2-pipe 1.6.2.

Official AAR metadata for the four artifacts requires compile SDK 36 and AGP
8.9.1+, which fit the current project. No desugaring setting change is required.
The CameraX 1.6 release notes describe a move to the CameraPipe camera stack,
so runtime preview/control checks accompany the dependency change.

`CameraStreamingTest` exercises the production scanner's actual `PreviewView`:
it waits for `STREAMING`, checks streaming after activity recreation, sends the
zoom slider input and waits for the camera's `linearZoom` state to change, then
taps the preview and checks continued streaming. Settings are restored after
each test. The zoom test explicitly skips cameras with no zoom capability.

## Verification

Tests run only on the separate `.validation` application on an API 36.1 emulator.

- `testDebugUnitTest`: 72 passed, zero failures/errors/skips.
- `assembleDebug`, `assembleDebugAndroidTest`, and `lintRelease`: passed.
- Fresh lint: 0 errors, 10 warnings (previously 13). Three CameraX update notices
  were resolved, leaving nine update notices and the retained plural warning.
  Core remains 1.18.0 and no lint suppressions were added.
- Portrait: both `CameraStreamingTest` tests passed without skips, including
  real preview streaming after recreation and camera zoom state changes.
- Nine scanner regression tests passed: continuous scanning and its UI lifecycle,
  engine selection/retry UI, real ML Kit availability and QR bitmap decoding,
  and edge-to-edge theme/keyboard checks.
- With display rotation locked to 90 degrees, both camera streaming/zoom tests
  passed again without skips. Total device test executions: 13 passed.
- `assembleRelease`: passed with R8 minification and resource shrinking. The
  generated APK is unsigned; no release upload or normal-app installation was
  performed. The emulator display was restored to portrait after testing.

Logs: `logs/build-camera-checks.log`, `logs/build-camera-update.log`,
`logs/build-camera-release.log`, and `build/camera-update/` (generated local files).

## Limits

An emulator is not a substitute for physical camera coverage. Physical autofocus
completion, torch/flash operation, real camera orientation and image quality,
and manufacturer-specific hardware remain unverified. A tap followed by stable
preview does not establish successful optical autofocus.

This update does not fix the separately recorded landscape cutout issue and does
not establish removal of Play Console edge-to-edge warnings. Core, Lifecycle,
and modern Compose upgrades need coordinated SDK/toolchain work.

References:

- https://developer.android.com/jetpack/androidx/releases/camera#1.6.2
- https://developer.android.com/jetpack/androidx/releases/core#1.19.1
- https://dl.google.com/dl/android/maven2/androidx/core/core/1.19.1/core-1.19.1-sources.jar

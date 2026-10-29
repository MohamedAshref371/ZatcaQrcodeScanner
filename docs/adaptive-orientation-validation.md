# Orientation and resizing validation

MainActivity does not declare a fixed orientation. The portrait-lock setting is
available and defaults to false when no value is saved. Existing saved choices
are preserved. The setting is hidden on large screens and in multi-window mode,
and reappears with its saved value when returning to a supported window.
When explicitly enabled, it requests portrait only on compact
screens (smallest width below 600dp) outside multi-window mode, including when
system auto-rotate is enabled. Large screens, multi-window mode, and disabling
the setting release the request to UNSPECIFIED. Configuration changes reevaluate
the policy, including folding/unfolding and resizing.

No aspect-ratio constraints, resizing restrictions, or compatibility opt-outs are
declared. Removing the manifest declaration addresses the exact restriction
listed in the Play notice, but only a fresh Play Console analysis can confirm
whether the notice disappears; runtime orientation requests remain on phones.

The camera already uses window constraints to place controls beside the preview
in landscape, updates camera rotation, and preserves the preview during handled
configuration changes. Settings, history, results, and statistics use scrolling
content. These code checks do not replace device testing.

## Automated checks

`OrientationPolicyTest` checks the opt-in default, 600dp boundary, and multi-window
exception. `AdaptiveOrientationUiTest` checks the unrestricted manifest and
disabled setting after launch/recreation, preference toggling and persistence,
then rotates through landscape and portrait and checks that settings remain
reachable. `CameraStreamingTest` covers both landscape directions, preview
streaming, retained preview, and retained zoom with the preference disabled.

Build and static checks:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintRelease
```

On a connected test device or emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.poetscorner.invoiceqr.AdaptiveOrientationUiTest,com.poetscorner.invoiceqr.CameraStreamingTest,com.poetscorner.invoiceqr.ResultRestorationTest'
```

## Device checks before release

Use Android 16 (API 36) and an older supported version such as Android 15
(API 35), each with a phone and a tablet/foldable configuration (at least 600dp
smallest display width). Also verify the current target API 37.

- Launch in portrait and landscape with system auto-rotate enabled. Repeat with
  system rotation locked to confirm that the app respects the user's choice.
- Rotate and resize in split-screen while using every navigation tab. Verify
  scrolling reaches all actions and system bars do not obscure content.
- Fold/unfold and resize while showing an invoice, a filtered history list, and
  settings. Confirm selected tabs, invoice details, and filters remain intact.
- Scan a real QR code in both landscape directions and portrait. Confirm preview
  proportions, crop guide alignment, focus, torch, zoom, and decoding.
- Repeat with Arabic/RTL, English, larger font settings, and the keyboard open.
- Enable portrait lock on a phone with system auto-rotate enabled: it must stay
  portrait. Disable it: landscape must become available again.
- Enable portrait lock on a tablet: landscape and resizing must remain available.
  On a foldable, verify the saved choice locks the compact outer display and
  releases orientation on the expanded display. Test split-screen entry/exit.
- Upgrade an installation with a saved portrait-lock choice and verify that the
  choice persists, while still leaving large screens unrestricted.

## Results (2026-10-29)

- Debug application and instrumentation APK builds passed; the new orientation
  tests compile.
- All 80 JVM unit tests passed with zero failures or errors, including both
  orientation-policy tests.
- Release lint passed with zero errors and two unrelated warnings:
  `AndroidGradlePluginVersion` and `PluralsCandidate`.
- Current merged Debug and Release main manifests contain no orientation,
  aspect-ratio, or resizing constraints.
- `check_localization.py` stops on the existing untranslated `engine_zxing`
  resource; that resource and the script were unchanged by this work.
- Device execution is pending: ADB reported no connected devices. The Android
  16/older-version matrix above has not been run.

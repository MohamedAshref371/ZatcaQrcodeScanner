# Edge-to-edge validation — 2026-10-23

Version: 1.0.3 (version code 6).

The app now enables edge-to-edge through `WindowCompat.enableEdgeToEdge(window)`.
System bar icon appearance follows the app's selected theme, including an override
of the device theme. The activity uses `adjustResize`. Only Scaffold content applies
IME padding, after consuming Scaffold padding to avoid applying insets twice.
The page navigation bar stays at the bottom, behind the keyboard while it is open.

## Verification

- `assembleDebug` and `assembleDebugAndroidTest` passed with
  `-PisolatedValidation=true -Pkotlin.incremental=false`.
- `EdgeToEdgeTest`: three tests passed in gesture navigation and the same three
  passed with three-button navigation on a temporary Android 16 / API 36.1 emulator.
  They check status/navigation bar control bounds, system icon appearance for
  explicit LIGHT/DARK app themes, and scrolling the manual inspection action
  above a visible software keyboard.
- After moving IME padding from the whole Scaffold to its content, the keyboard
  test passed again in both navigation modes. It also asserts that page navigation
  keeps the same vertical position before and after the keyboard opens.
- Light and dark scanner screens were inspected visually.
- `bundleRelease` and `lintRelease` passed: 0 errors, 56 warnings.
- The release bundle is `app/build/outputs/bundle/release/app-release.aab`.
  It is unsigned; generate a signed release before uploading. The existing signed
  bundle under `app/release` was not replaced.

## Remaining Play Console warning

The installed AndroidX Core 1.18.0 implementation of `WindowCompat.enableEdgeToEdge`
still calls `Window.setStatusBarColor`, `Window.setNavigationBarColor`, and uses
`SHORT_EDGES` on API 28–29 for compatibility. Inspection of the final release DEX
confirmed the two color calls remain, inlined into `MainActivity.onCreate` by R8;
the mapping file attributes them to WindowCompat. Moving to the recommended API
does **not** establish that Play's deprecated API warning will disappear.

No Play Console upload or verification was performed. Android 15, older Android
versions, physical cutout devices, and landscape layouts were not runtime tested.

References:

- https://developer.android.com/reference/androidx/core/view/WindowCompat
- https://developer.android.com/develop/ui/compose/system/insets-ui

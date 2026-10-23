# Edge-to-edge review: 1.0.4

Review date: 2026-10-23.

## Release artifact evidence

- Inspected local artifact: `app/release/app-release.aab`.
- SHA-256: `3553f8ebc2a7a855084c34bf9c125848fbe7c6a7cff514eebb6e3a2e5703e3b7`.
- Its protobuf manifest declares package `com.poetscorner.invoiceqr`, version
  `1.0.4`, version code `7`, minimum SDK `26`, and target SDK `36`.
- The bundle's embedded `proguard.map` matches
  `app/build/outputs/mapping/release/mapping.txt` by SHA-256.
- Android SDK 36.0.0 `dexdump -d` confirms calls to
  `Window.setStatusBarColor` and `Window.setNavigationBarColor` inside the final
  `MainActivity.onCreate`, at method instruction offsets `0x11` and `0x14`.
- The matching R8 mapping attributes this inlined code to
  `androidx.core.view.WindowCompat.enableEdgeToEdge`, source lines 159 and 160.
- AndroidX Core 1.18.0's local sources confirm both color calls are unconditional
  in that helper. The separate cutout configuration uses `SHORT_EDGES` on API
  28-29 and `ALWAYS` on API 30+.

These are ordinary Android SDK and archive inspection results. REA tools were
not connected and its CLI was not cached; no REA Evidence IDs were produced.
The local artifact was inspected, not a download from Play Console. Its identity
with the uploaded artifact remains unverified.

## Source review

- `MainActivity` explicitly enables edge-to-edge through `WindowCompat`.
- Material 3 `TopAppBar` and `NavigationBar` use their default insets.
- `Scaffold` padding is applied and consumed before IME padding on content.
- System bar icon appearance follows the selected light/dark app theme.
- The activity declares `adjustResize` for the software keyboard.
- Scanner, manual entry, results, history, statistics, and settings share this
  inset-aware content container. Dialogs use Material 3 components.
- No direct system bar color setters, legacy system UI flags, edge-to-edge
  opt-out, or screen-size arithmetic using `Configuration.screenWidthDp` or
  `screenHeightDp` were found in `app/src/main`.

Static review alone did not establish a defect. Runtime inspection subsequently
identified the horizontal cutout issue below.

## Finding: content can overlap a side cutout in landscape

On the temporary API 36.1 emulator, with the tall cutout overlay enabled and
rotation locked to 90 degrees, WindowManager reports a left cutout safe inset of
126 pixels and cutout bounds `[0,414][126,666]` in a 2400 x 1080 display.
UIAutomator reports the scanner's `Continuous scanning` text bounds as
`[90,418][344,519]`. These bounds intersect the cutout area. Content is therefore
not consistently kept within the horizontal cutout-safe region.

Evidence is retained locally under `build/edge-review/`: `landscape.png`,
`landscape-ui.xml`, and `window-landscape.txt`. The screenshot alone does not
establish the physical notch shape; the geometry comes from WindowManager.

Recommended correction: include display-cutout insets in the Scaffold content
insets, for example by combining `WindowInsets.systemBars` with
`WindowInsets.displayCutout`, and review horizontal protection for the app bars.
Preserve the existing content-only IME handling. Add a regression assertion for
horizontal content bounds with a side cutout: the current tests check vertical
app bar bounds and do not catch this case. This review does not change app code.

## Interpretation

The deprecated API warning is consistent with the actual local 1.0.4 release
bytecode and AndroidX helper implementation. Switching to this helper does not
remove those calls from the release. The warning alone does not demonstrate
overlapping controls. Removing it and validating visible layout are separate
checks.

## Validation

- Fresh offline `lintRelease`: 0 errors, 56 warnings.
- Fresh `assembleDebug` and `assembleDebugAndroidTest` passed, using
  `-PisolatedValidation=true`, `-Pkotlin.incremental=false`, and
  `-Pkotlin.compiler.execution.strategy=in-process`.
- Installed debug version: `1.0.4`, code `7`, application ID
  `com.poetscorner.invoiceqr.validation`, on a separate temporary API 36.1 AVD.
- All three existing `EdgeToEdgeTest` tests passed in each configuration:
  portrait/gesture navigation; portrait/three-button navigation with tall
  cutout; landscape/three-button navigation with tall cutout (nine passes).
- Tests cover vertical title/navigation bounds, light/dark system icon
  appearance, and manual inspection action reachability above the keyboard.
  Their success does not cover horizontal cutout protection; the measured
  runtime overlap above remains a finding despite those passes.
- Android 15/API 35, older Android releases, and physical devices were not
  runtime tested. No Play Console upload or reanalysis was performed.

Reference: https://developer.android.com/about/versions/15/behavior-changes-15#edge-to-edge

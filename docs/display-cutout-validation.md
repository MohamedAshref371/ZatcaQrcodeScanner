# Landscape display-cutout protection

Date: 2026-11-02. App version: 1.1.7 (17).

The shared `Scaffold` now uses the union of system-bar and display-cutout insets
for content padding. The title bar applies its top and horizontal safe insets;
the bottom navigation applies its bottom and horizontal safe insets. The camera
preview still fills the window, while its controls use the safe content padding.
This addresses the horizontal overlap recorded in
`edge-to-edge-review-1.0.4.md` without changing edge-to-edge setup APIs.

## Validation

- `assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` passed with the
  separate `.validation` package.
- On the read-only Pixel 10a Android 17/API 37 emulator, the tall display-cutout
  overlay produced a 126 px side inset in landscape.
- `DisplayCutoutUiTest` passed with the left cutout and English LTR, then passed
  with the right cutout and Arabic RTL. It checks the title, scanner heading,
  continuous-scan label, bottom navigation label, and History heading against
  the actual cutout insets. Without a side cutout, it skips rather than reporting
  a misleading pass.
- A direct UI hierarchy check in left-cutout landscape placed the scanner
  heading at x=190 and the continuous-scan label at x=227, both outside the
  126 px unsafe region. In right-cutout Arabic RTL, their right edges were
  x=2234 and x=2197, below the safe boundary x=2298 on a 2424 px display.
  A screenshot is saved locally at `build/cutout-scanner.png`.
- On a connected Samsung SM-A205F running Android 11/API 30, the physical notch
  reported a 53 px left inset in landscape. The same isolated test passed with
  Arabic RTL on that device. A second attempt in the opposite direction could
  not reach the app because the phone went to sleep; it is not a layout result.

The older Compose-based `EdgeToEdgeTest` stalled during activity startup on this
temporary emulator, before returning a result. The new regression test uses
Android accessibility bounds and completed in both configurations. The normal
app also rendered and its scanner and History layouts were inspected directly.
The emulator overlay and rotation were restored, and the emulator was closed.
The phone's original portrait rotation lock was restored and both temporary
validation packages were removed. The physical right-cutout orientation and a
Play Console upload were not tested.

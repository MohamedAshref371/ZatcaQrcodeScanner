# Title bar scrolling

Date: 2026-10-25

The shared title bar now uses Material 3 `enterAlwaysScrollBehavior`: scrolling
toward later content hides the bar, and reversing the gesture reveals it without
returning to the start of the page. The connection wraps the content of Result
(including manual entry), History, Statistics and Settings. Camera keeps its
title fixed. All content tabs share one title position. Entering Camera reveals
the shared title immediately and leaves it visible in subsequent tabs. Neither
Scaffold nor the title is keyed by destination. Bottom navigation stays fixed.
System bar insets remain managed by the existing layout. The sections below
record earlier implementations and their validation; this paragraph describes
the current behavior.

The change is in `ScannerApp.kt`; `MainActivity.kt` is unchanged.

## Validation of the initial implementation

- `assembleDebug assembleDebugAndroidTest lintDebug -PisolatedValidation=true`
  succeeded. Lint: 0 errors, 1 existing `PluralsCandidate` warning for the tag-ID
  error message; no new warnings.
- On the Pixel 10a API 37 emulator, `TitleBarScrollTest` and `EdgeToEdgeTest`:
  **5 tests passed**. Checked hiding and reversing before the list reaches its
  beginning, fixed bottom navigation, the initial title reset on tab changes, fixed camera
  title, system bar insets, theme icons and keyboard access.
- `StatisticsNavigationTest`, `HistoryNavigationTest` and `ResultActionsUiTest`:
  **4 tests passed**. Covered restoring statistics/group scroll positions,
  history navigation/filter controls and result sharing/copy actions.
- Device tests used only the `.validation` package on the emulator. No physical
  phone installation or production app data clearing. Emulator closed afterward.

Evidence: `build-title-scroll-validation.log` and
`build/title-scroll/{device-tests.txt,navigation-tests.txt}`. The normal package
debug build is recorded in `build/title-scroll/normal-debug-build.log`.

This run covers portrait API 37. It does not resolve the previously recorded
language-test failures or landscape cutout finding.

API reference: https://developer.android.com/develop/ui/compose/components/app-bars#scroll-behavior

## Follow-up: retain the title position per tab

Replaced the state reset on tab changes with five independently remembered,
saveable title states, one per tab. The camera still does not attach a scroll
behavior. Returning to another tab restores its previous hidden or visible
position instead of expanding it automatically.

`assembleDebug assembleDebugAndroidTest -PisolatedValidation=true` succeeded.
The updated `TitleBarScrollTest` passed **2 tests** on the API 37 emulator:
independent tab positions, preserving both hidden and visible states on return,
fixed camera title, and the existing hide/reveal and fixed navigation behavior.
Evidence: `build/title-scroll/retained-build.log` and
`build/title-scroll/retained-device-tests.txt`. The normal debug package build
is recorded in `build/title-scroll/retained-normal-debug-build.log`.

## Follow-up: settings scroll and stable tab layout

Settings now receives a `LazyListState` remembered outside the destination
content, so leaving and returning to the tab preserves its item and pixel
offset. Each destination has a keyed Scaffold composition. Its title, scroll
connection and content padding are created together using the destination's
retained state; the preceding tab's layout and color-animation state are not
reused. The title states and saved History/Statistics/Result UI remain outside
this destination key.

API 37 emulator: **9 tests passed** (`TitleBarScrollTest`, `ResultScrollUiTest`,
`StatisticsNavigationTest`, `EdgeToEdgeTest`). The updated tests verify Settings
scroll restoration and compare title height and list bounds immediately after
one frame of a tab switch with the same values 600 ms later, using a manually
controlled Compose clock. Both camera expansion and Settings' restored collapsed
title are checked. Result tab restoration/new-card reset, statistics navigation,
system bar insets, theme icons and keyboard access also passed.

Evidence: `build/title-scroll/stable-tabs-build.log`,
`build/title-scroll/stable-tabs-device-tests.txt`, and
`build/title-scroll/stable-tabs-normal-build.log`. Tests used only the emulator's
`.validation` package; emulator closed afterward. No physical phone changes.
The final normal `assembleDebug lintDebug` build succeeded with 0 Lint errors
and the single previously recorded `PluralsCandidate` warning; no new warnings.

## Follow-up: reduce tab-switch work

The user reported slow tab switching after the keyed Scaffold change above.
That scope was unnecessarily broad: it recreated Scaffold and bottom navigation
on every tab switch. The current code keeps both in composition and applies
`key(screen)` only to the top title bar, so its internal presentation starts in
the destination's saved state without recreating the surrounding layout.
Settings' lifted list state and the Result/History/Statistics state holders are
unchanged. The earlier nine passing UI cases established correctness, not
physical-device performance; no measured phone speedup is claimed here.

Validation: debug and instrumentation builds passed. All **5 tests** in
`TitleBarScrollTest` and `ResultScrollUiTest` passed on the API 37 emulator,
including first-frame stability, retained Settings/Result scroll and new-invoice
reset. Evidence: `build/title-scroll/lightweight-tabs-build.log`,
`build/title-scroll/lightweight-tabs-tests.txt`, and
`build/title-scroll/lightweight-tabs-normal-build.log`. Emulator closed; no
physical-device installation.

## Current behavior: one shared title state

At the user's request, removed all per-tab title states and the destination key
around TopAppBar. There is one remembered title state and scroll behavior for
all content pages. Switching tabs preserves that shared position. Camera has no
collapsing behavior; a SideEffect resets the shared height/content offsets before
layout, so its title is visible immediately and remains visible on subsequent
tabs. Scrolling is disabled for the shared behavior while Camera is active.
Opening another invoice resets only the result content, not the shared title.
Settings/Result content scroll restoration is retained.

Final API 37 emulator run: **8 tests passed** (`TitleBarScrollTest`,
`ResultScrollUiTest`, `EdgeToEdgeTest`). Checked shared hidden/visible title across
all tabs, Camera revealing it for every tab, first-frame stability, content scroll
restoration/new-card reset, system bars and keyboard access.

One initial test APK contained the pre-edit assertion ordering and was rebuilt.
Keyboard retries also exposed a SystemUI ANR dialog owning window focus. Choosing
Wait on that emulator dialog restored focus; the unchanged app and test APKs then
passed all eight cases. No app workaround or test suppression was added.

Evidence: `build/title-scroll/shared-title-build.log`,
`build/title-scroll/shared-title-test-rebuild.log`,
`build/title-scroll/shared-title-final-tests.txt`, and
`build/title-scroll/shared-title-normal-build.log`. Earlier attempts are retained
alongside them. Emulator closed; no physical phone installation or measured
phone-performance claim.

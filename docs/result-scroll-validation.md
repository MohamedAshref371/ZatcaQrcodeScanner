# Result scroll restoration

Date: 2026-10-25

Returning to the current invoice through the Result tab now restores its list
position and expanded details. A saveable state holder keeps the current
result's UI while another tab is displayed. It is replaced when the result
identity (saved row ID, check time or payload) changes, so opening another invoice
starts at the beginning. Following the later shared-title change, the title
keeps its global position; only entering Camera forces it visible. The previous result's UI is
discarded rather than retaining state for every invoice ever opened.

Changes are in `ScannerApp.kt`; no ViewModel or database behavior changed.

## Device validation

The validation below predates the shared-title behavior. Its new-card test now
checks that the list starts at the top while the shared title stays in place.

On the Pixel 10a API 37 emulator, final run: **5 tests passed**:

- `ResultScrollUiTest`: Result-tab return restores scroll position, title position
  and expanded checks; clicking another actual History card resets scroll and
  reveals the title and result header.
- `TitleBarScrollTest`: both existing hide/reveal and independent tab-state cases.
- `StatisticsNavigationTest`: overview/group scroll survives invoice navigation.

Earlier attempts exposed two test-harness issues, now corrected: the JUnit setup
method needed an explicit Unit return, and fixtures inserted through a separate
Room instance needed an explicit pager refresh and readiness wait before the
card click. Their logs are retained; the final passing run is
`build/title-scroll/result-scroll-final-tests.txt`.

Debug and instrumentation APK builds passed. The final normal debug build and
`lintDebug` also passed: 0 errors and the single existing `PluralsCandidate`
warning, with no new warnings. Normal debug build and Lint output:
`build/title-scroll/result-scroll-normal-build.log`. Device testing used only
the `.validation` package on the emulator, which was closed afterward. No
physical phone was changed. This run covers portrait API 37, not process-death
restoration or the previously recorded language/landscape findings.

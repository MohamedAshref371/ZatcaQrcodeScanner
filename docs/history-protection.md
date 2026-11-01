# History preservation and result restoration

The current experimental database schema is version 1, exported to `app/schemas/com.poetscorner.invoiceqr.data.ScanDatabase/1.json`. There are no migrations for older internal builds. Clear app data before testing an incompatible installation.

Current device tests:

```powershell
./gradlew.bat -PisolatedValidation=true '-Pandroid.testInstrumentationRunnerArguments.class=com.poetscorner.invoiceqr.DatabaseProtectionTest,com.poetscorner.invoiceqr.ResultRestorationTest,com.poetscorner.invoiceqr.ContinuousScanningTest,com.poetscorner.invoiceqr.HistoryNavigationTest' :app:connectedDebugAndroidTest
```

`DatabaseProtectionTest` checks that reopening the current database retains an invoice. `ResultRestorationTest` parcels saved state and constructs a new ViewModel with a fresh store, checking invoice identity, original check time, retained tab, dismissal and deleted rows.

For an actual process-death check, use the isolated validation app on a device:

1. Scan an invoice, note its check time, and leave its result visible.
2. Press Home, then run `adb shell am kill com.poetscorner.invoiceqr.validation` while the app remains in the background.
3. Reopen the existing task from Recents. Check that the same result and check time appear, with no extra history row.
4. Repeat while viewing History or Settings after opening a result. Verify that the same tab returns and the Result tab still opens the invoice.
5. Return to the scanner before backgrounding and killing the process again. Verify that the dismissed result does not return.

Do not use force-stop, swipe the task away or use activity recreation as a substitute for the process-death check. Saved state belongs to the task. Only the saved row ID and tab are stored in its Bundle; the invoice itself stays in Room. Unsaved results cannot be recovered from a row ID.

## Validation performed

- 55 local unit tests passed; debug APK and device-test APK built successfully.
- Lint completed with no errors (39 existing dependency/style warnings).
- On the API 36.1 emulator, all 9 targeted tests passed: `DatabaseProtectionTest`, `ResultRestorationTest`, `ContinuousScanningTest`, `ScanRejectionTest` and `HistoryNavigationTest`. The isolated validation app had camera permission granted before the UI tests.
- Actual background process termination was checked three times with `am kill`, confirming a different PID after each relaunch. The result and original check time returned, History remained selected when restored, and a dismissed result stayed dismissed. These checks used the isolated validation app and a synthetic invoice.

Reference: [SavedStateHandle](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate).

# Manual history backup and file operations

Settings contains **Backup and restore** immediately above **Appearance & app**, in English and Arabic. Backup always includes the entire history, independently of the current search or date filters. Import merges records; it never clears the existing history. The completion message reports added and duplicate counts.

The version 1 JSON format is independent of display language and database schema version:

```json
{
  "format": "invoiceqr-history-backup",
  "version": 1,
  "invoices": [
    { "raw": "<original Base64 QR text>", "checkedAt": 1792580000000 }
  ]
}
```

Original QR text and check timestamps are retained. Local checks, extracted fields and normalized duplicate keys are rebuilt with the current parser. Imported row IDs are allocated locally; existing duplicate invoices retain their original content, IDs and check times. Preferences and supporter purchases are not transferred. CSV and the existing localized JSON exports remain summary formats, and are rejected by the backup importer.

Imports first copy the selected document into private cache, with a 64 MiB file limit. JSON is read one record at a time, with a bounded reader to prevent oversized JSON strings from allocating arbitrarily large buffers. Unsupported versions, duplicate JSON keys, missing fields, wrong types, invalid timestamps, malformed QR payloads, trailing content and truncated JSON are rejected. Parseable invoices whose local field checks fail can still be restored, matching normal scan behavior. The entire restore is one Room transaction, so invalid input or cancellation rolls back newly inserted records.

Backup writes a consistent database snapshot in batches of 50 raw payloads. Export retains the selected search/date snapshot and its existing localized JSON/CSV contents, including spreadsheet formula protection. Both first prepare a private temporary file. The provider receives the output only after preparation succeeds, outside the database transaction. A provider failure can still leave an incomplete destination file, and is reported as a failure. Temporary cache files are deleted on normal success, failure and cancellation; a process killed mid-operation may leave cache files for Android to clear.

An observable operation state drives a progress banner on every tab, shows processed/total invoices during preparation and a separate saving stage. File actions are disabled while a picker or operation is active, and the ViewModel independently rejects overlapping operations. Cancelling a picker re-enables the actions. Coroutine cancellation is propagated and does not display an export/import failure. Operations survive configuration changes through the ViewModel; interrupted file operations do not resume after process death.

Run device checks in the separate validation app:

```powershell
./gradlew.bat -PisolatedValidation=true '-Pandroid.testInstrumentationRunnerArguments.class=com.poetscorner.invoiceqr.HistoryBackupTest,com.poetscorner.invoiceqr.DataTransferTest,com.poetscorner.invoiceqr.BackupSettingsUiTest,com.poetscorner.invoiceqr.HistoryStorageTest,com.poetscorner.invoiceqr.FilteredHistoryExportTest,com.poetscorner.invoiceqr.HistoryNavigationTest' :app:connectedDebugAndroidTest
```

Grant the validation app camera permission before UI tests, so the initial scanner permission dialog does not obscure the app.

Validation completed on the API 36.1 emulator: all **16** targeted tests passed, including a 235-invoice round trip, duplicate imports, malformed/truncated/unsupported inputs, transaction rollback on cancellation, size limits, filtered exports, failed destination recovery, Settings section order and both system-picker cancellation paths. The **55** local unit tests, localization audit (227 translated keys), debug build and Lint also passed. Lint reports no errors and 39 existing warnings. The validation app is separate from the regular installation.

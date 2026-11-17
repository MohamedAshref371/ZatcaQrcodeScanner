# Stage 5: shared feature coordinator and native document adapters

Android's ScannerViewModel now delegates feature operations to ScannerController in shared/commonMain.
IosInvoiceSession supplies the same controller to IosScanner, which hosts the existing ScannerApp.
The five Compose screens, navigation, result state, validation, duplicate handling, import summaries,
statistics selection, annotation validation, review tracking and transfer state therefore have one implementation.

The Android SavedStateHandle and existing DataStore files remain in use. StatisticsFilterStore preserves
Android's statistics_filters keys and validation; the native preference adapters only read and write strings.
Database schema version, migrations, application ID, SDK levels and signing are unchanged.

DatabaseBackup owns snapshot creation and transactional restore. Both adapters stream the same versioned
backup format, preserve timestamps and annotations, rebuild derived invoice fields, retain existing duplicates,
and roll back invalid or cancelled restores. The platform-neutral JSON token reader bounds individual records;
the adapters enforce the existing 64 MiB input limit before opening the write transaction.

## Native iOS components

- IosScannerFiles uses sandbox URLs and PDFKit/UIImage rendering, then passes decoded invoices to the shared
  controller. It writes a temporary file, waits for the system export picker, and removes it after completion
  or cancellation. Progress and success messages come from shared transfer logic.
- IosInvoiceSession owns the database and coroutine lifetime. Document pickers supply sandbox copies;
  Photos transfers ownership of its temporary copies to the session until the shared import completes.
  No second large-file copy runs on the UI thread. The session contains no invoice rules.
- IosSystemActions offers Photos and Files through the existing import action. PHPicker is needed to access
  Photos; UIDocumentPicker is needed for PDFs, arbitrary images, JSON imports and document exports. Failed
  Photos item-provider results remain unreadable items in the shared batch instead of disappearing silently.
- The existing AVFoundation camera and PDFKit adapters remain the native bridges. zxing-cpp remains the sole
  decoder, with the existing shared recovery and overlay logic. The previously documented 1080p QHD fallback
  is unchanged.

## Validation scope

The iOS integration suite renders a real QR into a photo and a two-page PDF, imports them through native
file decoding and ScannerController, checks duplicate/result/history behavior, exports filtered CSV and
statistics, round-trips a backup through the native file adapter, and checks save-picker cancellation.
Additional database tests cover annotations, duplicate restore, invalid-backup rollback and cancellation.
Shared tests check strict streaming JSON and a queued invalidation during history page loading.
Android validation includes transfer, import, continuous scanning, saved-state restoration and statistics
navigation in addition to the existing UI and database compatibility selection.

This is not a runnable Xcode application yet. Stage 6 supplies the app target, usage descriptions and icon,
then compares all five screens in English and Arabic. Actual picker presentation and interaction still need
that app host. Live camera, torch and tap-to-focus still require a physical iPhone.

# Invoice QR Reader — ZATCA QR Scanner

English | [العربية](README-ar.md)

An Android app for reading Saudi e-invoice QR codes, viewing invoice details, running local structural checks, and keeping a searchable invoice history. Built with Kotlin, Jetpack Compose, Material 3, CameraX, Room, and DataStore.

Requires **Android 8.0 (API 26)** or later. Supports English (LTR), Arabic (RTL), light and dark modes, and free default, gold, purple and blue app appearances.

**The app performs local structural checks only. It does not certify tax compliance, verify cryptographic signatures, or establish official invoice acceptance. It is independent of ZATCA.**

## Features

- Read invoice QR codes with the camera, choose images and PDFs together through the same import button, receive images and PDFs shared by other apps, or paste Base64 manually. A single image opens its result; multiple files are saved automatically with an import summary. Each PDF is scanned for all distinct invoice QR codes across its pages.
- Batch imports show the current file and PDF page. Cancelling keeps completed files in history and leaves the unfinished file unsaved; each file is saved in one database transaction.
- Use ZXing by default, or download the optional Google ML Kit scanning component.
- Scan individual invoices or enable continuous scanning with a session counter and result notifications.
- View seller, VAT number, timestamp, total including VAT, tax, and phase-two fields when present.
- See separate result levels for QR structure, core fields, phase-two fields, cryptographic authenticity and official acceptance. Expand advanced fields, check details, and the original QR content when needed.
- Share a single invoice summary from the result header, including the optional hash and local phase-one/phase-two check result, or copy its VAT number using the adjacent icon. Shared field labels follow the app language and original values are preserved.
- Search saved invoices by seller or VAT number and filter by invoice date or check date.
- Export matching invoices as JSON or CSV through the system file picker.
- Use all invoice features for free, without ads or an account.


## Offline scanning and engine downloads

Both scanning engines process invoice images on the device:

| Engine | Availability | Setup |
|---|---|---|
| ZXing | Included and selected by default | Works offline immediately |
| Google ML Kit | Optional component managed by Google Play services | May require an internet connection to download before use; scanning works offline afterward |

The current ML Kit dependency is `com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1`. Its model is **not bundled in the APK**. Settings check whether the component is already available and show a download button when needed, with progress, paused, installation, failure, and retry states. ML Kit cannot be selected until availability is confirmed. If a previously selected ML Kit component becomes unavailable, scanning uses ZXing temporarily.

The component can already be available through Google Play services, including from another app. Reinstalling this app does not guarantee that another download will be necessary. The radio buttons indicate the selected engine; component availability is separate from selection.

Engine preferences are stored in DataStore and apply to both camera and gallery scanning. ZXing-specific options appear only when ZXing is selected. Resolution, torch, tap-to-focus, and zoom controls are shared. **Crop scan area**, below **Scan frequency**, is enabled by default and restricts both engines to the visible square. Turning it off hides the square and shading and scans the whole visible camera preview. The choice is saved across restarts and does not affect gallery images. ML Kit receives an NV21 image of the selected area with the frame rotation and plane stride taken into account.

## Results, history, and export

Duplicate detection covers camera scans, images, PDFs, and manual Base64 input. The app compares a unique, indexed SHA-256 key of Base64 normalized for whitespace and padding, while preserving the originally saved text. Lookup and insertion run in one database transaction. A single duplicate scan opens the saved invoice. Batch imports count each distinct invoice once: repeated QR codes across PDF pages, render passes, or files in the same import are ignored; only invoices already in history are reported as existing. Neither adds an entry or changes the original check date.

Results show the seller, VAT number, timestamp, total, and tax in field order. For phase-two candidates, the hash is also shown among the main fields. Signature, public key, and stamp are in an advanced section that starts collapsed. Failed checks appear in the result summary; detailed checks and original QR text can be expanded separately. Malformed Base64/TLV is rejected without opening a result or adding history. Parseable invoices with failed field checks can still be saved for review.

History supports partial seller/VAT searches, including VAT numbers entered with Arabic or Persian digits. The **Search** button opens a date filter with one active source: invoice date or check date. Ranges include both endpoints. Invoice dates use the day written in tag 3; check dates use the device's local time. Canceling the dialog leaves the applied filter unchanged. Entries with missing or invalid invoice dates are excluded only when an invoice-date range is active.

Search and display fields are stored when an invoice is first saved. SQLite performs filtering, and the UI loads summaries in pages of **50** without loading raw Base64. The complete payload is fetched when an invoice is opened. The history performance report includes a [10,000-invoice device test (Arabic)](docs/history-performance.md).

JSON and CSV export use the same search and date filters as the displayed history. The filter is captured when export starts, including across activity recreation while choosing a destination. With no filters, the entire history is exported. Export buttons are disabled while results are updating or no matching invoices exist.

Exports include the seller, VAT number, invoice date, total, tax, and hash where present. Field names/column headings use the app language; original invoice values are preserved. CSV cells are quoted and protected against spreadsheet formula injection. Export streams batches of **200** rows within one transaction to keep a consistent snapshot, without loading the full history or output file into memory.

Export shows invoice progress followed by a saving stage. Export, backup and import are mutually exclusive; actions are also disabled while a file picker is open. Output is prepared in a private temporary file before copying it to the selected destination, keeping cloud/provider writes outside the database transaction. Cancellation is propagated rather than reported as a failure, and temporary files are removed when the operation finishes.

**Backup and restore** is in Settings immediately above **Appearance & app**. Create backup saves the complete history, including original QR text and original check times, as a language-independent, versioned JSON document. Import backup merges those records into the current history without deleting existing invoices. Normalized duplicates are skipped, preserving the existing record and check time. Extracted fields and local checks are rebuilt using the current parser; settings and internal database row IDs are not imported. Ordinary JSON/CSV exports are summaries and cannot restore original QR payloads.

Import is one transaction: malformed, unsupported, truncated or cancelled backups leave no partially imported rows. The result reports added and duplicate counts. Backup files support up to **64 MiB**; processing streams records and bounds individual JSON values. Selected input is staged in private cache before opening the transaction. Manual backups are unencrypted files at the destination you choose and remain there after uninstalling the app.

## Language and appearance

Settings includes a **Privacy policy** link that opens the public bilingual policy in a browser after confirmation; internet access is required. The URL comes from `publishing/privacy-policy-url.txt`, also used for Play Console. Edit the canonical `publishing/privacy-policy-ar.md` and `privacy-policy-en.md` files and run `python tools/prepare_privacy.py` to generate the bilingual `privacy-policy.md`, then update the existing public Gist. Policy content is no longer bundled in the app, so online text updates do not require an app release. The developer is Mohamed Ashraf, the Google Play publisher is vStudio, and the support/privacy email is `afi4test2@gmail.com`. Publication/update instructions are in [publishing/privacy-publishing-ar.md](publishing/privacy-publishing-ar.md). Google SDK technical-data handling and local review-timing records are disclosed separately from invoice processing.

English resources are in `app/src/main/res/values/strings.xml`; Arabic translations are in `values-ar/strings.xml`. `resources.properties` declares English as the default resource language. The interface, camera controls, messages, field labels, parser errors, and check results use Android string resources.

The first option under **Settings → Appearance & app** is **App language**, with Device language (default), العربية and English. The choice is saved across restarts on Android 8 and later. On Android 13 and later, it also syncs with **Android Settings → Apps → Invoice QR Reader → Language**. Changing the language updates text and layout direction while keeping the current screen and saved invoices. `generateLocaleConfig` and `localeFilters` declare English and Arabic support.

Layout direction follows the language, while Base64 and numeric values remain readable left to right. Invoice content is never translated. Messages and checks use `UiText` resource references resolved at display time, so retained state can be rendered in the current language.

## Open and run the project

1. Open the repository in **Android Studio Rabbit 1 (2026.2.1) or newer**, with support for **Android Gradle Plugin 9.4.1** and the project's **Gradle 9.8.2 Wrapper**. Use the bundled JBR 25, matching the project's Gradle daemon criteria.
2. Install Android SDK Platform **37** and the required Build Tools. Set `sdk.dir` in your local `local.properties` file. The app targets API **37** with minimum API **26**.
3. Sync Gradle. Downloading build dependencies initially requires internet access.
4. Run the `app` module on a device and grant camera permission. Gallery input remains available without camera access.
5. Adjust the scanning engine and camera settings. ZXing's Try harder option and HD resolution are enabled by default. CameraX chooses an actual resolution supported by the device.

From PowerShell:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Release builds:

```powershell
./gradlew.bat :app:assembleRelease :app:bundleRelease
```

The project does not configure a release signing key. `assembleRelease` produces `app/build/outputs/apk/release/app-release-unsigned.apk`; release artifacts need appropriate signing before distribution. Store listings, icons, privacy drafts, and signing instructions are in [publishing/README.md (Arabic)](publishing/README.md). The current version is `1.2.3`, version code `23`.

## ZXing native wrapper

The project uses the official Android wrapper:

```kotlin
implementation("io.github.zxing-cpp:android:3.1.1")
```

Its AAR includes the C++/JNI libraries, so consuming it does not require CMake or the NDK. `scanner/QrScanner.kt` configures `BarcodeReader.Options` with `QR_CODE` only. ZXing supports Try harder, denoise, rotation, inverted colors, binarizer selection, and downscaling for camera and gallery input.

### Optional: build the wrapper from source

Install CMake and an NDK supporting C++20, matching the pinned wrapper's toolchain requirements. Then build the official source:

```powershell
git clone --branch v3.1.1 --depth 1 --recurse-submodules https://github.com/zxing-cpp/zxing-cpp.git
cd zxing-cpp/wrappers/android
./gradlew.bat :zxingcpp:assembleRelease
```

The wrapper already defines JNI/CMake integration in `zxingcpp/src/main/cpp/CMakeLists.txt`. Pin `ndkVersion` in that library module if needed. Copy `zxingcpp/build/outputs/aar/zxingcpp-release.aar` into this project's `app/libs/` directory, and **replace** the Maven dependency with:

```kotlin
implementation(files("libs/zxingcpp-release.aar"))
```

Do not include both dependencies: they contain duplicate classes and native libraries. CameraX is declared directly in this project, including when a local AAR is used.

## Project structure and camera processing

```text
app/src/main/java/com/poetscorner/invoiceqr/
  MainActivity.kt
  domain/ZatcaParser.kt                    # Base64, TLV, invoice fields and checks
  scanner/QrScanner.kt                     # ZXing options, gallery decoding and EXIF
  scanner/QrDecoder.kt                     # Camera/gallery adapters for both engines
  scanner/GoogleMlKitModuleInstaller.kt    # Google Play services module installation
  scanner/MlKitModuleController.kt         # Availability and download state
  data/LocalData.kt                        # Room and settings DataStore
  data/HistoryExporter.kt                  # Streaming JSON/CSV export
  data/ReviewRepository.kt                 # Local usage and one-time review eligibility
  ui/ScannerViewModel.kt                   # App state and background operations
  ui/CameraScanner.kt                      # CameraX, permission and camera controls
  ui/ScannerApp.kt                         # Navigation, results and settings
  ui/ScanEnginePicker.kt                   # Engine selection and download controls
  ui/HistoryScreen.kt                      # Search, summary and paged history
  ui/theme/                               # Material 3 colors, shapes and typography
```

CameraX uses one analysis executor with `STRATEGY_KEEP_ONLY_LATEST` and closes `ImageProxy` in `finally`. Normal, fast, and fastest modes use minimum scheduling intervals of 300 ms, 100 ms, and no added delay respectively; these are not guaranteed frame rates. Single-scan mode stops after an accepted result; continuous mode keeps scanning and reports saved, duplicate, and review states without navigating away from the camera. Leaving the scanner disposes its camera use cases.

Image and PDF decoding, parsing, and export run off the UI thread. Images are corrected for EXIF orientation and downsampled when either dimension exceeds 4096 pixels to bound memory use. PDFs are copied to private cache while being read, limited to 64 MiB, rendered one page at a time, and deleted after processing. Each PDF can contribute multiple distinct invoices; unrelated or malformed QR codes do not stop the page search. Password-protected PDFs are not supported on the app's older Android versions.

**Lock app to portrait**, under Appearance & app in Settings, is disabled by default (including for existing installations without a saved choice). Enabling it locks supported compact fullscreen devices to portrait; otherwise the app follows the phone's rotation preference. The choice is saved in DataStore. When rotation is allowed, screen rotation updates the existing camera use cases instead of recreating the Activity or rebinding on preview size changes, preserving zoom and the continuous-scan delivery gate. Display rotation updates include 180-degree turns. The crop guide uses the unobstructed space between the app bars and, in landscape, beside the controls (mirrored for RTL). Its rectangle is mapped through CameraX sensor transforms into each analysis buffer, so the visible guide and decoded region stay aligned without a fixed ViewPort. With cropping disabled, the same mapping scans the full visible preview.

## Parsing rules and limitations

- Tags 1–5 represent seller, VAT number, timestamp, total including VAT, and VAT amount. Tag 6 is the invoice hash, tag 7 the ECDSA signature, tag 8 the **public key**, and tag 9 the technical certification authority's public-key signature/stamp. Tag 8 is not a complete X.509 certificate.
- Tags 6 and 7 preserve their existing Base64 text; they are not encoded a second time. Binary values in tags 8 and 9 are converted to Base64 for display.
- The app classifies an invoice as simplified when tag 9 exists and standard otherwise. This is a stamp-based heuristic, not an XML document-type lookup. An incomplete code can therefore be classified as standard. An empty tag 9 still counts as present but fails its content check.
- Any tag from 6–9 makes the code a phase-two candidate and requires tags 6, 7, and 8. Without these tags, it is treated as phase one; an entirely missing phase-two section cannot reveal the intended phase. Only checks relevant to the inferred phase are displayed.
- Checks include required values, a nonblank seller, a 15-digit ASCII VAT number beginning and ending with `3`, and a valid ISO 8601 timestamp. A tag 3 timestamp without a timezone is interpreted as Saudi time (`UTC+03:00`); `Z` and explicit offsets are accepted. Up to nine fractional-second digits are supported. Original timestamp text is retained. For example, `2024-11-29T14:25:00` and `2024-11-29T11:25:00Z` describe the same instant.
- Amounts must be nonnegative decimals with at most two fractional digits, and tax must not exceed the total. No fixed VAT rate is assumed.
- The parser tries original one-byte TLV lengths first, then extended BER `0x81`/`0x82` lengths if complete parsing fails. Original lengths take precedence in ambiguous cases. Truncated/out-of-bounds values, duplicate or unsupported tags outside 1–9, and invalid UTF-8 in textual tags 1–7 are rejected. Input is limited to 100,000 characters.
- Checks on tags 6–9 confirm data presence and structure, not signature authenticity, hash correctness, or certificate trust.
- Scanning quality depends on camera hardware, lighting, focus, and QR size. No performance equivalence with the reference scanner is claimed.

## Local data and review requests

Room stores invoice history in the app's private storage. Database-level encryption is not implemented. Backup is disabled, export is manual through the system picker, and broad storage permission is not required. Uninstalling the app removes its local history.

The current experimental database schema is version **1**. It has no migration code. To test an older internal build against this schema, clear the app's data first. Settings and review usage are stored independently from history.

The current result's database row ID and selected tab are saved through `SavedStateHandle`. When Android recreates a background-killed process, the invoice is loaded from Room with its original check time, without inserting another row or switching away from the restored tab. Returning to the scanner clears the result reference. Deleted invoices are not recreated; results that failed to save cannot be restored. This is task restoration, not persistence across force-stop or dismissal from Recents.

The app makes at most one automatic Google Play review attempt after all three conditions are met: seven days since first tracked use, ten different saved invoices, and use on three different days. The request waits for an appropriate history-screen pause. Deleting history does not reset eligibility or the attempt flag. A separate **Rate the app** action opens the store listing. Google Play decides whether to display its review dialog; the app cannot determine whether a rating was submitted.

## Verification

Run the local checks:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
python tools/check_localization.py
```

Unit coverage includes parsing, dates, Base64/TLV edge cases, scanning gates, search/export rules, review eligibility, and ML Kit availability/download states. Device tests cover navigation, history paging, continuous scanning, localization, and engine selection. A built device-test APK does not mean those tests have run on a device.

To run device tests in a separate `.validation` app without replacing the normal installation:

```powershell
./gradlew.bat -PisolatedValidation=true :app:connectedDebugAndroidTest
```

If that package name is already in use with another signature, choose a separate suffix with `-PisolatedValidationSuffix=.validation.codex`.

To run only localization tests, add:

```text
-Pandroid.testInstrumentationRunnerArguments.class=com.poetscorner.invoiceqr.LocalizationTest
```

Measured history performance and the associated device-test scope are documented in [docs/history-performance.md (Arabic)](docs/history-performance.md). Compose Foundation is pinned to `1.9.2` to keep compile/runtime APIs aligned after a previous `FlowRow` ABI mismatch.

Before distribution, check real camera behavior, printed and screen-displayed QR codes, orientation changes, large fonts, both languages/themes, permission denial, torch/zoom, gallery input, export files, and real ML Kit downloads. A live ML Kit download is not established by the simulated-state tests.

## References

- [Official zxing-cpp Android wrapper](https://github.com/zxing-cpp/zxing-cpp/tree/v3.1.1/wrappers/android)
- [Official BarcodeReader options](https://github.com/zxing-cpp/zxing-cpp/blob/v3.1.1/wrappers/android/zxingcpp/src/main/java/zxingcpp/BarcodeReader.kt)
- [Google ML Kit barcode scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android)
- [ML Kit model installation paths](https://developers.google.com/ml-kit/tips/installation-paths)
- [Android per-app languages](https://developer.android.com/guide/topics/resources/app-languages)
- [Reference project: hid-barcode-scanner](https://github.com/Fabi019/hid-barcode-scanner)
- [ZATCA QR code specification](https://zatca.gov.sa/ar/E-Invoicing/SystemsDevelopers/Documents/QRCodeCreation.pdf)

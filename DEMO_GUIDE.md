# Test the SEC Rename demo

## Batch files

Open **Batch files**, choose separate local source and output folders, and optionally import the Excel company list. Tap **Start batch rename** to analyze the first 10 supported PDFs or images in natural filename order. Each filename opens the document preview; edit the suggested company and save that row individually. After a row is saved or identified as already renamed, it leaves the pending list and the next pending file fills the open slot. The source folder is read only.

Batch still needs an end-to-end check on the target device. Use fictional sample files before bank documents. If a completed copy is removed outside the app, check the output folder and export history before starting a new batch.

Release: **0.5.1-demo**, version code 21. [Android APK](./app/build/outputs/apk/debug/app-debug.apk). Install over the previous demo using the same application ID and local signing key. Do not uninstall or clear data to update: that removes private captures, settings, and history.

APK SHA-256: `f627979e6f0588ed1442f3e53f7a42cf3f619c75c0b696dc2351e5109b4fac9e`.

## What is in this release

- Renamed copies now go directly into the selected output folder. No automatic SEC Export subfolder is created. Existing export subfolders and their files are preserved and still checked for duplicates.

- A home screen separates **Single Document** from **Batch files**. Batch uses local source and output folders, an optional company list, and groups of 10 supported PDFs or images.
- Single Document offers **Scan with camera** and **Open file**. The system file/photo picker requests local-only files; unsupported cloud/third-party providers are rejected.
- The first **Scan with camera** or **Open file** action asks where renamed files should be saved if no usable output folder exists. **Choose folder** opens Android's local folder picker; after validation, the original action continues once. Cancel/Back stays quiet and does not launch an input picker or scanner. A valid remembered folder skips setup. The Single Document output folder is remembered after reopening; **Save copies to** changes it without starting a source action. Choose a dedicated local internal-storage folder, not a restricted storage/Downloads root. Copies go directly inside it.
- A local working copy is made without changing the selected original. Exact duplicate detection compares hashes under the output location before OCR and again before export, including older export subfolders.
- The clickable filename attachment opens an on-demand fullscreen viewer with pinch/double-tap zoom, panning, page navigation, Reset zoom, and Higher detail. The initial page render is bounded to a 2400-pixel long edge; Higher detail requests up to 4000. Zoom cannot recover detail absent from the original scan.
- A verified save shows a compact Material Snackbar with **File saved / Check file** above **Export history / About** for three seconds, with hold and accessibility support. The app title remains visible. The footer and Snackbar share a measured bottom bar, so body controls are not covered; adaptive scrolling handles constrained windows. No empty message slot is kept after dismissal. Full filenames, dates, folders, and Check file remain in history.
- **Export history** has no automatic expiry and no 30-entry cap. Records are grouped by local export date, newest day first. Fifty records load initially; More exposes older entries, with lazy rows to keep the dialog responsive. New records use verified completion time; older records use their recorded export-start time, falling back to the journal file date when necessary. Incomplete or unreadable records stay visible without a completed-file action. Dates do not come from document text or the original source file date.
- The document area starts empty on launch and clears after successful saving or genuine app backgrounding. The output folder remains remembered. Selecting a file/folder in Android pickers or using the scanner is part of the active workflow and does not itself clear the selection; rotation also preserves the active session. An unfinished review must be selected/scanned again after leaving. No document/name draft is restored.

Android document rendering uses platform `PdfRenderer`; the shared viewer uses the basic local-image [ZoomImage](https://github.com/panpf/zoomimage) component pinned to 1.3.0, compatible with the project's Kotlin/Compose baseline. No network image-loader integration is added. Its license is bundled in the APK's local assets.

## Install on your S23 Ultra

1. Transfer the APK by USB or the approved private distribution method, then install the update over the earlier demo.
2. Confirm version **0.5.1-demo** in Android's app information. Record Android/One UI versions when reporting a problem.
3. Open **Single Document**, then tap **Open file** or **Scan with camera**. If prompted, choose an output folder such as `Documents/SEC_Demo_Output`. The app remembers it and continues the action. Existing valid settings need no new setup.
4. Use fictional or bank-approved test documents. The initial app minimum remains Android 8/API 26; actual device and Google Play scanner compatibility still require validation.

## Test on the PC emulator

The emulator and Google Play Android 15 image are installed under ignored `.tools` storage. Run VS Code **Terminal > Run Task > Android: Open demo emulator**, or **Android: Build and run demo** after code changes. The launch script copies project demo PDFs/JPGs/PNGs into `Download/SEC_Demo_Source`, creates `Download/SEC_Demo_Output`, and opens the APK without uninstalling it.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Run-AndroidDemo.ps1 -Build
```

In the emulator, enter Single Document, choose `Download/SEC_Demo_Output` once, then use **Open file** to select a document from `Download/SEC_Demo_Source`. This runs the Android app, not a packaged Windows version.

The main screen stays non-scrolling at normal sizes. When a short window, larger text, or the keyboard reduces usable space, it becomes scrollable so source, review, and footer actions remain reachable. Wider screens keep side-by-side source/review panels. Scan with camera and Open file stay after the relevant content. The header handles safe system/cutout space and the body consumes Scaffold insets before keyboard padding. Test actual portrait, landscape, 200% text, and keyboard layouts on the target device.

## File review and safe export

1. Choose an output folder separate from the original document folder. Local external-storage source parents are checked when available. For media-provider photos, the app works from its private copy and never writes to the original.
2. Choose one PDF, JPG, or PNG. The app makes a private working copy, hashes its contents, and compares supported files under the chosen output root. Hashing does not run OCR.
3. If no matching saved copy exists, tap **Read company name**. Observe page progress and disabled conflicting actions.
4. Tap the filename attachment to check the certificate holder. Zoom and inspect every relevant PDF page; closing the viewer preserves the name field and review position.
5. Correct the company name if needed. **More** opens possible names, OCR evidence, and Read again. Invalid names cannot be saved.
6. Tap **Save renamed copy**, review the name and destination, then choose Yes, save. After verification, the review clears and File saved / Check file appears above the footer for about three seconds. The app title and footer buttons stay visible. Hold the message to read longer; Check file opens the saved folder. After dismissal, use Export history for details and the file action. Verify the message does not cover Scan with camera, Open file, or footer controls.
7. Open the exported file in an approved local viewer. Use **Export history** to inspect completed or incomplete records.

Expected names for supplied examples:

| File | Expected certificate holder |
| --- | --- |
| SEC_Demo_01.pdf | SILVERBAY MERIDIAN TRADING CORPORATION |
| Sample SEC Secretary’s Certificate-2.png | ABC SOLUTIONS INC. |
| Sample Philippine Secretary’s Certificate-1.png | SUNRIDGE INDUSTRIES INC. |
| scan001.pdf / scan002.pdf | NORTHSTAR TRADING CORPORATION |
| needs-review.pdf | No automatic suggestion; manual review required |

## Duplicate and recovery checks

- Re-select an already exported file, including after reopening the app. If matching bytes still exist under the same output root, show **Document already renamed** and block OCR/export. A changed filename alone does not bypass the check.
- Confirm this duplicate status appears under the attachment without a popup or additional inline issue. Confirm the header has no notification bell.
- Open **Export history**, then **Check file** on a completed entry. Verify the file manager opens that entry's folder, including for an older export subfolder and after changing the current output setting. Return and confirm the output setting did not change. Incomplete/unreadable records remain visible without a completed-file action.
- Different content for the same company remains eligible. Occupied names directly in the selected output folder receive `(1)`, `(2)`, etc. No file is overwritten.
- If a completed record exists but successful destination inspection finds no matching copy, continue through normal OCR, filename review, and save confirmation. No missing-copy warning or extra recovery dialog is shown. Previous export records remain intact and duplicate inspection runs again before saving.
- Permission errors and unreadable files are not treated as proof that a previous copy is missing. Recover access before continuing.
- Uncertain/incomplete exports require output inspection. No blind automatic retry or deletion of partial/completed output is provided.
- Deduplication covers supported PDF/JPG/PNG content and is byte-based. Re-photographing, editing, or recompressing the same physical certificate can create different hashes and is not reliable semantic duplicate detection.
- Output scanning has a defensive limit of 10,000 entries and 32 folder levels. If exceeded, choose a narrower output location; the app does not silently conclude there are no duplicates.

## Camera, photos, and offline checks

Prepare the Google scanner with connectivity if its resources are unavailable. Capture a fictional document, review crop/rotation, and confirm. Camera results are scanner-generated PDFs; untouched sensor-photo archival is not provided. The scanner's built-in gallery entry remains available, while **Open file** imports an existing local JPG/PNG directly without requiring scanner preparation.

After scanner preparation, enable airplane mode and turn Wi-Fi off. Reopen and test local-file import, photo selection, viewing, OCR, duplicate detection, and export. Then try another camera scan. Google Play services components can require re-preparation if removed/reset; do not assume downloads can never recur.

Reopen the app and confirm the output location is restored. Verify the document area is empty, including after Home/background and a successful save. Confirm the destination is still remembered. File/folder pickers and rotation should preserve an active review until it is saved or the app genuinely backgrounds. Test portrait/landscape, keyboard entry, enlarged text, TalkBack, image rotation, long filenames, protected/corrupt PDFs, and unavailable folder access on the target device.

## Scope, privacy, and remaining validation

This is a device demo, not a bank production release. Foreground batch review and optional local company-list import are implemented. Batch capacity validation, recovery of in-progress edits, Windows packaging, background execution, and an on-device language-model fallback remain future work. See [BATCH_PROCESSING_PLAN.md](./BATCH_PROCESSING_PLAN.md) for the target scope and gaps.

Single Document does not restore document/name drafts or automatically resume interrupted work. Backgrounding clears the visible document; late results are ignored. Existing export journals and verification guards remain. Unused private imported copies are removed after retirement and at cold start, after readers finish; unresolved or unreadable export records prevent unsafe cleanup. A new camera working PDF is removed only after its output is hash-verified and its completed journal is committed, with a final source-hash check before deletion. Failed/unexported scans and older retained captures are kept. Cleanup never removes imported originals, renamed outputs, or journals. Deletion failure retains the private file. History has no automatic expiry but app-data clearing/uninstall still removes it. Drive uploads are handled separately by the user/bank, outside this offline app.

App internet/network-state permissions are excluded and automatic app backup is disabled. Google Play services and storage-provider/cloud-sync behavior are separate from the app's own permissions. No document-upload endpoint, analytics, or OS/lock-screen company-name notification is added. Screenshot protection remains enabled, including inherited protection in the viewer dialog; verify on the physical device.

Previous 0.4.2 single-document validation: 98 tests passed (81 shared and 17 Android-module JVM tests), Android assembly and lint passed, and the listed API 35 emulator checks covered destination prompts, folder picker behavior, remembered destinations, and scanner launch. This does not validate the current batch release. Physical-device camera capture, batch scale/performance, the unknown client tablet, and real-world extraction accuracy remain separate validation work.

Previous full suite (0.4.0): all 92 tests passed (75 shared and 17 Android-module JVM tests), including nine real-filesystem private-copy cleanup tests, three timezone/date tests, and expanded permanent-history grouping checks. These cover cleanup scope, unresolved-record protection, preservation of originals/outputs, camera source-hash matching, local-date boundaries, older history beyond 30 entries, and existing extraction/export/session policies. They do not certify the unknown client tablet or real-world extraction accuracy.

Previous 0.4.1 preview: Android assembly and lint passed (0 errors, 14 advisory warnings). APK signature verification passed and app Internet/network-state permissions remain excluded. Extraction, cleanup, duplicate detection, and journal logic did not change, so the full automated suite was not repeated for this presentation update. The runtime check found and corrected a Snackbar width constraint that initially hid its message; final native accessibility checks observed both File saved and Check file. Snackbar bounds were [42,2074][1038,2200], above the footer action bounds [42,2212][335,2338], with main input buttons uncovered and SEC Rename still visible. After the timeout the Snackbar disappeared and the footer stayed fixed. The final fictional exported PDF matched its original hash, all pre-existing output/journal hashes were preserved, and its private import was cleaned up. Three fictional Snackbar exports were created during the layout checks; their records remain in history.

The earlier 0.4.1 preview used an unsaved **snackbar-preview-demo.pdf**, read as **SNACKBAR PREVIEW DEMO CORPORATION**. For the current build, choose a fictional document to test saving. The Snackbar's Check file route remains available in export history after dismissal. Physical device, large-text/keyboard Snackbar-specific cases still need validation.

The headless API 35 emulator update preserved all nine existing journal hashes and all nine existing output hashes. Cold start removed 19 unused private imports. A new fictional two-page PDF was read and exported with matching SHA-256; its working import was then removed and a tenth history record retained. The save header was observed while held, with File saved and Check file entirely above the main content. The output-folder bounds stayed exactly [42,346][736,430] during and after feedback; branding returned after dismissal. History displayed November 3, November 1, and October 31 dividers, and Check file opened the correct folder. At 200% text in landscape, scrolling exposed the Save and Export history actions.

Follow-up verification completed after the temporary approval-review usage limit cleared. Original text size (1.0) and portrait/free rotation were restored. With the portrait keyboard shown, the company field and primary actions remained visible; Save opened the confirmation and Edit name returned focus with the keyboard shown. No actual scrolling was needed in that portrait case because all controls fit. The earlier 200% landscape test verified the scroll fallback when content exceeded the viewport. After configuration changes settled, a genuine background/resume cycle cleared the fictional review and retired its import. Final checks found zero private imports, ten journal records, and ten output files with all baseline hashes preserved; the keyboard test created no extra export. The headless test emulator was then closed. Physical S23 camera/scanner cleanup and additional device-specific accessibility cases remain to be tested.

Android assembly and lint passed for 0.3.8: 0 lint errors and 13 non-blocking warnings (four dependency-update notices, eight KTX suggestions including the new history URI parsing, and one ExifInterface recommendation). The APK signature verifies; package inspection confirms version code 15, API 26 minimum, API 35 target, and no app Internet/network-state permissions.

For 0.3.9, shared JVM compilation, Android assembly, and Android lint passed (0 errors, 13 existing non-blocking warnings). The APK signature verifies and package inspection confirms version code 16, API 26 minimum, API 35 target, and no app Internet/network-state permissions. This update changes feedback layout/timing; the last full automated suite remains the 77-test run from 0.3.8. The reserved footer bar, timer/hold behavior, Check file action, keyboard/text scaling, and portrait/landscape appearance still need device runtime checks.

Emulator runtime checks confirmed the home/Single Document split, remembered output and duplicate prevention after restart, detection of an older `scan001.pdf` export before OCR, on-demand rendering of a fictional two-page PDF, Previous/Next, Higher detail, double-tap zoom and Reset zoom, preservation of the company field after closing the viewer, and a verified export of `VIEWER DEMO CORPORATION.pdf`. Android window inspection confirms `SECURE` on both the activity and viewer dialog. Moving only that fictional export outside the output root caused a missing-copy prompt after restart and blocked reading/saving pending confirmation. Allow re-export re-enabled reading while saving still required fresh review. The test export was restored without overwriting and its hash verified; imported originals were not modified.

The 0.3.1 UI checks confirmed that the main review screen has no page-scroll container in portrait or landscape, all primary controls remain visible at the emulator default text size, OCR evidence opens in a dialog, Save opens the company/filename confirmation, Edit name focuses the input with the cursor at the end, and dismissing or editing creates no output. After editing the fictional name, Yes, save created exactly one copy whose SHA-256 matched the original. Completed state offers Details and prevents another save. Full software-keyboard sizing and enlarged text still require the physical-device checks below.

The 0.3.2 direct-output check saved two different fictional PDFs with the same company name directly in the selected folder as DIRECT OUTPUT DEMO CORPORATION.pdf and DIRECT OUTPUT DEMO CORPORATION (1).pdf. Both saved hashes matched their respective originals; original hashes and all four pre-existing subfolders were unchanged. No extra output subfolder was created. A previously saved file in an older SEC Export subfolder was still detected before OCR. The folder comparison initially reported a test-helper array-shape mismatch; normalizing the arrays confirmed identical folder lists.

The 0.3.3 notification/navigation check confirmed silent document-picker cancellation, normal import and single-candidate OCR without adding notifications, removal of the extra confirmation sentence, and one completed-save notification containing the actual filename and output directory. Check file opened the correct folder in the Android Files app, where the saved PDF was visible. Returning preserved the selected input and output folder. The notification center contained only the pre-existing duplicate issue and the completed save, with Check file on that save; no routine-ready or cancellation entries appeared. Saved bytes matched the original SHA-256. The file was not automatically highlighted. Alternative file-manager behavior and the fallback browser still require physical-device validation.

The 0.3.4 session checks confirmed an empty Single Document screen on update/cold launch, the Save copies to button, and the remembered destination. Pressing Android Home during OCR and resuming the same activity (HOT launch) cleared the document and review without late OCR results returning. File selection, output-picker cancellation, and portrait/landscape rotation preserved an active review as intended. A verified save returned the screen to Choose a document, with no attachment or name field; the completed-save message and Check file action remained in Notifications. The saved SHA-256 matched the original, and the legacy single.draft preference was absent. Clearing selection does not delete private working copies, camera PDFs, imported originals, or exported copies. Google scanner lifecycle behavior still needs physical-device validation.

The 0.3.5 layout check confirmed Scan with camera and Open file near the bottom of the empty portrait screen, above the history/about footer. Opening a fictional PDF through the relocated button worked normally. With an extracted company name, both input buttons remained below the filename and Save controls in portrait. In landscape they sat at the bottom of the document column; the preview attachment, company field, More menu, Save action, and footer all stayed visible without a page-scroll container. The emulator was returned to portrait/free rotation.

The 0.3.6 correction removes the empty-state Choose a document text and places the input buttons directly after the content instead of anchoring them at the bottom. Android assembly and lint passed (0 errors, 12 existing warnings). The emulator empty-state check confirmed the output-folder row followed by Scan with camera / Open file; the old label was absent. No extraction, lifecycle, or export logic changed; the last full automated suite remains the 71-test run from 0.3.5.

The 0.3.7 change supersedes the historical missing-copy confirmation checks above. After successful destination inspection, a missing completed copy no longer produces a warning or recovery dialog; normal OCR, filename review, and save confirmation remain required. Existing duplicates show Document already renamed. Prior journal records, rechecks before saving, and access/uncertain-export guards remain in place. The demo emulator was not running during this update, so the changed workflow still needs runtime validation on the emulator or S23 Ultra.

The 0.3.8 change supersedes the notification-center checks above: the bell, unread count, and session message list are removed. Duplicate status is displayed only under the file. Only verified saves produce a popup; errors/warnings remain inline with Details and recovery actions. Completed export-history records reconstruct their original saved folder from the recorded file URI, including legacy subfolders, without rewriting the journals. The emulator was not running during this update; repeat the duplicate, history, folder-access, portrait/landscape, and successful-save checks on the emulator or S23 Ultra.

The 0.3.9 change supersedes the floating-save-popup behavior above: a compact File saved / Check file bar now occupies reserved layout space immediately above Export history. It has no close button, filename, or path; history retains those details and the file action. Its default timeout is four seconds, with hold and accessibility support. No extraction, duplicate detection, export, or history-storage logic changed.

Android emulator results do not certify the unknown client tablet or establish real-world extraction accuracy. Automated Windows visual capture was previously unavailable; Android accessibility/runtime checks were used. Physical-device pinch/pan quality, TalkBack, text scaling, image orientation, photo-picker behavior, scanner/offline readiness, memory/storage stress, and revoked-access cases still need device validation. Report the version, filename, action, expected/actual result, and exact message without sending sensitive document contents.

Historical notification-center checks verified message retention after banners disappeared. That center was removed in 0.3.8; persistent export history now provides saved-folder navigation.

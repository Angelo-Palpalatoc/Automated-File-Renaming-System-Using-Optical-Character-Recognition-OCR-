# Try the Android demo on your S23 Ultra

Built demo: [app-debug.apk](./app/build/outputs/apk/debug/app-debug.apk), version `0.2.4-demo` (version code 6), approximately 51.5 MiB. Application-owned source and tests are now entirely Kotlin. Signature verification passed; 49 automated tests passed; lint has 0 errors and 6 non-blocking warnings. Package inspection found no app internet/network-state permissions. The previous APK was tried on the S23 Ultra; the extraction updates and gallery scanner flow still need physical-device retesting.

APK SHA-256: `a7752ce5aa96bccebf03eca479a3c586c89602245dd5e7013ae48bdbc56f7c57`.

## Update from the previous demo

Transfer the newly built APK to the phone and install it over an earlier demo (`0.2.0-demo`, `0.2.1-demo`, or `0.2.2-demo` / `0.2.3-demo`). It uses the same local demo signing key and application ID. Do not uninstall or clear app data as part of this update; app-private camera captures are removed by uninstall/data clearing.

Retest one of the ten synthetic incorporation certificates, then both Sample Secretary's Certificate images. Expected company holders for the latter are **ABC SOLUTIONS INC.** and **SUNRIDGE INDUSTRIES INC.**. Their bank mentions should not become the company name. These new cases passed local OCR-transcript regression tests; their Android OCR output remains to be checked.

Part 1 remains OCR plus programmed extraction rules, review, and verified-copy export. The local AI fallback has not been added. Report whether the company field is correct and whether the OCR evidence contains the name if a file is missed.

## 1. Transfer and install

### Test on the PC with the Android emulator

The project includes [Setup-Emulator.ps1](./scripts/Setup-Emulator.ps1) and [Run-AndroidDemo.ps1](./scripts/Run-AndroidDemo.ps1). The emulator, Google Play Android 15 x86_64 image, and virtual device live under the ignored `.tools` directory. This runs the Android APK; it is not a Windows version of the app.

Setup status (2026-10-31): emulator 37.2.13 and the Google Play Android 15 x86_64 image are installed, and `SEC_Rename_API_35` is running. APK installation, sample transfer, and app startup have been verified. Scanner camera/gallery preparation and physical-device checks remain separate validation items.

After setup, use VS Code **Terminal > Run Task > Android: Open demo emulator**. Choose **Android: Build and run demo** after code changes. You can also run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Run-AndroidDemo.ps1 -Build
```

The script opens the virtual device, installs/updates the APK without uninstalling it, copies project PDF/JPG/PNG demo documents into **Download/SEC_Demo_Source**, creates **Download/SEC_Demo_Output**, and launches SEC Rename. Select those two folders inside the app using Android's Files picker. Virtual-device files are separate from the original PC files.

If hardware acceleration is unavailable, save your work and restart Windows after Windows Hypervisor Platform has been enabled. The script does not restart the PC or wipe the virtual device. First scanner use may still download Google Play services resources. Camera/gallery scanner availability must be checked on the virtual device; successful emulator testing does not replace final S23 Ultra camera/offline/storage checks. Use fictional project samples for emulator testing.

### Install on the phone

After a successful build, transfer `app/build/outputs/apk/debug/app-debug.apk` to your phone by USB or a privately shared download link. This file is the application; do not upload bank documents to distribute it.

Open the APK on the phone and follow Android's installation prompts. If Android permits installation from that source, grant it only as needed. Bank-managed device restrictions can prevent sideloading. Do not disable device protection just to force an installation; use the approved method if blocked.

The current application minimum is Android 8.0/API 26, but the actual installed OS/One UI version and Google Play services availability still need device validation. Record your versions from **Settings → About phone → Software information** when reporting a problem.

## 2. Folder-import demonstration

1. In Samsung My Files, create separate local folders such as `Documents/SEC Demo Source` and `Documents/SEC Demo Output`.
2. Transfer the four PDFs from [demo-documents](./demo-documents/) into the source folder. These are explicitly fictional.
3. Open **SEC Rename**. Choose the source folder through the system Files picker and allow folder access.
4. Choose the output folder. It must be outside the source folder. Select a local subfolder rather than the Downloads root, which Android may restrict.
5. Select `scan001.pdf`, then **Read company name**. Inspect the first-page preview, candidate, and OCR evidence. Edit the company name if needed.
6. Check **I checked the company name against the certificate**, then **Save verified renamed copy**.
7. Repeat with `scan002.pdf`. The same company should receive an output name ending in `(1).pdf` in the batch folder.
8. `needs-review.pdf` deliberately has no certificate-holder anchor. Verify that the app requires a manually entered name rather than guessing.
9. Inspect source and output folders in My Files. Source names/content should be unchanged. The output folder contains a newly created `SEC Export ...` batch folder.

Output names are sanitized for filesystem compatibility. A visible integrity failure or incomplete history entry means the output must not be treated as completed.

## 3. Camera and existing-photo demonstration

1. Start with Wi-Fi/mobile data available and tap **Scan with camera**. Google Play services may need time to prepare scanner resources.
2. Capture a fictional printed sample, or tap the scanner's gallery button to choose an existing photo. Review cropping/rotation and confirm. This demo uses basic scanner mode to avoid ML-enabled stain/finger removal. The scanner creates a new PDF; it does not rename or modify the original gallery photo.
3. The scanner PDF is copied into app-private local capture storage. Choose an output location, read its company name, review it, and export a verified copy.
4. Camera sources in app-private storage are not independent backups; uninstalling or clearing app data removes them. Keep an exported copy before removing the app.

Repeat using a local gallery photo in airplane mode after scanner preparation. Verify photo selection, cropping, OCR, review, and export, and confirm the original photo is unchanged. This gallery path still requires a functioning scanner; use source-folder import for local JPG/PNG files if scanner setup is unavailable. Direct folder import preserves the original image bytes, while gallery scanning produces a processed PDF.

## 4. Offline check

After successful initial scanner preparation, enable airplane mode and turn Wi-Fi off. Close and reopen the app, then try a new scan, folder import, OCR, and export. This is a required physical-device check, not something desktop unit tests establish.

If scanner resources are unavailable, the app should explain the issue and still permit supported local-file import. Do not assume Google Play services resource downloads can never recur.

## 5. Demo limitations and reports

### Feedback and presentation in 0.2.4

The native interface groups work into Import, Review, and Export. The status panel stays visible while scrolling. Brief snackbars acknowledge completed actions; persistent text explains what happened and what to do next. Color is paired with text and symbols, and status changes use polite accessibility announcements. Wider screens show import and review side by side. Files can be searched by filename; OCR evidence is collapsed until requested.

| Situation | User feedback / behavior |
| --- | --- |
| Source or output picker cancelled | Informational message; current selection stays unchanged |
| Empty folder or unsupported files | Warning with supported formats; skipped file/subfolder counts where applicable |
| More than 200 supported files | Explicit pilot-limit warning |
| Unsupported provider / source also chosen as output | Error with a folder-selection action; imported source files remain untouched |
| Scanner preparing / unavailable / cancelled | Preparation state, import fallback, or cancellation acknowledgement; repeated scan taps are blocked during preparation |
| OCR running | Completed-page count and progress bar; conflicting actions disabled |
| No text, no reliable name, or multiple candidates | Review warning; manual entry or candidate selection; no automatic export |
| Invalid or excessive filename | Inline field error and disabled save; editing resets review confirmation |
| Save prerequisites missing | Visible explanation under the disabled save button |
| Export running | Separate copy, verification, and publication stages; repeated saves blocked |
| Export completed | Persistent receipt with actual filename and batch folder, plus a success notification |
| Export uncertain / incomplete | Error directs user to history and output inspection; no automatic retry |
| Export history | Human-readable completed/incomplete labels and local dates; unreadable records treated as incomplete |

These are in-app notices. No OS notification permission, push service, analytics, or document-upload endpoint was added. The screenshot-protection flag remains enabled. App-background recovery, a durable automatic batch queue, full-page navigation, and company-list import remain later work.

Verification: 49 automated tests pass, including six export-readiness checks. Emulator accessibility checks confirmed picker cancellation, document import, the no-pattern/manual-review warning, disabled controls during OCR, rejection of source/output equivalence, the direct read/review actions, and a successful export of `scan001.pdf` as `NORTHSTAR TRADING CORPORATION.pdf`. The receipt, repeat-save block, and dated completed-history entry were verified. The PC source, emulator source, and renamed output all had SHA-256 `ccefb5bdd04b54dfcee5d397bac19a21c6e6ff374be9da4d8344b91808c1152e`. This is a workflow check on a fictional sample, not an extraction-accuracy benchmark. Windows visual capture failed with `FrameArrived timed out` / `window capture timed out`, so automated visual inspection could not be completed; Android accessibility inspection was used instead. Physical-device, TalkBack, tablet landscape, low-storage, revoked-access, and interrupted-export checks still need validation before bank release.

Design references: [Android snackbar guidance](https://developer.android.com/develop/ui/compose/components/snackbar), [progress indicators](https://developer.android.com/develop/ui/compose/components/progress), and [accessible semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics).

- One reviewed document at a time. No company-list import or full batch automation yet.
- Proposed full-folder batch workflow, limits, and review UI are documented in [BATCH_PROCESSING_PLAN.md](./BATCH_PROCESSING_PLAN.md). That document is a plan, not a feature included in this APK.
- First-page preview only; for a multipage certificate inspect all relevant pages with an approved local viewer before confirming the name.
- Export history reports completed and incomplete operations. Do not blindly repeat an interrupted export; inspect the batch output first. Automatic recovery is a later milestone.
- No Windows app or iPad app has been packaged. Shared JVM compilation tests are not desktop-app validation.
- No physical-device results have been recorded yet. Note the app message, action, Android/One UI version, and whether the source was camera/PDF/image when reporting problems. Avoid sending sensitive document contents.

The app blocks ordinary screenshots as a precaution, so reporting the exact message as text may be easier.

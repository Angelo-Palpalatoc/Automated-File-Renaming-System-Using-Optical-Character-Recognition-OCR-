# SEC Rename — Android device demo

Kotlin Multiplatform + Compose Multiplatform Android-first prototype. Shared naming rules and UI compile for Android and JVM; the Windows application and Tesseract adapter are planned next. All application-owned source and tests are Kotlin. Android-specific OCR and read-only document access live in [app/src/main/kotlin](./app/src/main/kotlin/); shared UI and extraction rules live in [shared/src/commonMain/kotlin](./shared/src/commonMain/kotlin/).

The Android SDK, Gradle, and Kotlin/JVM toolchain still require a JDK. Kotlin source can also use Android/JVM APIs and third-party libraries implemented in Java or native code; this does not require Java source in our application. The retired prototype Java extractor has been removed, and its seven original behavior checks now exercise the shared Kotlin rules.

Built update: `0.2.4-demo` adds a guided import/review/export interface, persistent success/warning/error feedback with recovery actions, OCR page progress, filename validation, verified-copy receipts, and readable export history. Application-owned source and tests remain Kotlin; incorporation-clause and Secretary's Certificate extraction fixes are retained. The updated APK built successfully; 49 automated tests passed, lint has 0 errors and 6 non-blocking warnings, and its signature verifies. Windows OCR transcript tests are separate from Android device OCR validation. Install this update over `0.2.0-demo` or `0.2.1-demo` to try it.

Part 1 is the OCR/rules baseline with reviewed, source-preserving export. Its current next step is S23 Ultra retesting. The proposed local-language-model fallback is part 2 and is deferred; no local LLM runtime or model has been added.

## Current demo

- Android camera capture or existing-photo selection through Google ML Kit Document Scanner, basic crop/rotate mode. Tap the gallery button inside the scanner to select a photo. This creates a processed PDF without changing the original photo. Scanner components may need internet for initial preparation through Google Play services.
- Bundled Latin-script ML Kit OCR for local PDF/JPG/PNG processing.
- Source folder selection and separate output-parent selection, restricted to internal device storage.
- One document at a time: company-name candidate, first-page preview, editable accepted name, OCR evidence, and explicit review checkbox.
- Export original bytes into a new batch folder under the output parent. Never rename, write, or delete imported originals. Duplicate output names receive `(1)`, `(2)`, etc.
- SHA-256 snapshot before/after OCR, bounded streaming copy, output verification, and a durable operation journal. Incomplete exports remain visibly incomplete in history; automatic restart/resume is not implemented yet.
- PSBank-inspired working blue/red theme. Exact official color tokens have not been verified.

This is a pilot for fictional or approved test documents, not a production bank release. Company-list import, multi-document batch processing, zoom/full-page navigation, automated recovery, Windows packaging, and any iPadOS target are not implemented in this demo.

## Build

The repository includes a Gradle 8.11.1 wrapper with distribution checksum. Tested dependency baseline: JDK 17, Android SDK 35, AGP 8.9.2, Kotlin 2.1.20, Compose Multiplatform 1.8.2. These pinned versions are used for this prototype, not asserted to be the latest versions.

With a configured JDK 17/SDK installation:

```powershell
.\gradlew.bat :shared:desktopTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

For a project-local toolchain on Windows, inspect [Setup-Android.ps1](./scripts/Setup-Android.ps1), then run it only if you agree to the SDK licenses:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Setup-Android.ps1 -AcceptAndroidLicenses
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Build-Android.ps1
```

Toolchains/caches and the demo signing key live in ignored `.tools/`. Keep the demo signing key if you want subsequent APK updates to install over the current demo. This debug key is not a production bank signing key. Dependency downloads require network access on the development computer; that is separate from document processing on the phone.

Build output: `app/build/outputs/apk/debug/app-debug.apk`.

## Try on Samsung Galaxy S23 Ultra

See [DEMO_GUIDE.md](./DEMO_GUIDE.md) for transfer, installation, scanner preparation, and offline checks. Copy [fictional samples](./demo-documents/) to the phone for folder-import tests. They are generated test documents, not genuine SEC certificates or a representative accuracy benchmark.

## Important boundaries

- No successful build implies successful device testing. Scanner preparation, runtime permissions, actual document-provider behavior, copy integrity on-device, and airplane-mode operation must be checked on the S23 Ultra.
- The manifest removes app internet/network-state permissions and disables automatic backup. Inspect the final APK permissions. Google Play services is separate and can download scanner resources; our permission settings do not restrict it.
- There are no document-upload endpoints, email integrations, or analytics configured by our code. ML Kit is proprietary; bank approval and dependency/network review remain release requirements.
- Camera scan sources live in app-private storage and are removed on uninstall/app-data clearing. Export important captures before uninstalling. The scanner does not provide untouched sensor photos through this workflow; the PDF returned by it is the source digital scan.
- A successful export preserves source bytes and verifies output contents. Local journals are not tamper-proof audit records. Output-folder access controls and independent backups remain bank responsibilities.
- Only tested local document providers should be supported. The demo rejects cloud/third-party providers and output folders inside the selected source. Storage-provider publication is not claimed atomic under arbitrary concurrent external changes.
- JPG/PNG orientation correction and automated deskew remain limited; Google-scanned PDF pages normally arrive already adjusted. Poor or rotated scans can require manual review.

Product scope and decisions: [APP_SPECIFICATION.md](./APP_SPECIFICATION.md).

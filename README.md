# SEC Rename — Android device demo

See [ARCHITECTURE.md](./ARCHITECTURE.md) for current component and document-flow diagrams. Open it in VS Code Markdown Preview with Ctrl+Shift+V.

Kotlin Multiplatform + Compose Multiplatform Android-first prototype. Android includes Single Document and foreground Batch Files workflows, optional local XLSX company-list matching, shared naming rules, and UI. The Windows application and Tesseract adapter are planned next. All application-owned source and tests are Kotlin. Android-specific OCR and document access live in [app/src/main/kotlin](./app/src/main/kotlin/); shared UI, queue, matcher, and extraction rules live in [shared/src/commonMain/kotlin](./shared/src/commonMain/kotlin/).

The Android SDK, Gradle, and Kotlin/JVM toolchain still require a JDK. Kotlin source can also use Android/JVM APIs and third-party libraries implemented in Java or native code; this does not require Java source in our application. The retired prototype Java extractor has been removed, and its seven original behavior checks now exercise the shared Kotlin rules.

Current release: `0.5.1-demo` (version code 21). The app includes a 10-item batch review window that analyzes pending documents and refills a slot after each saved or already-processed file. Batch progress is reconstructed from verified output copies when the same source/output folders are reopened; unsaved OCR suggestions are not restored. See [DEMO_GUIDE.md](./DEMO_GUIDE.md) for current behavior and device checks.

Part 1 is the OCR/rules baseline with reviewed, source-preserving export. Its current next step is S23 Ultra retesting. The proposed local-language-model fallback is part 2 and is deferred; no local LLM runtime or model has been added.

## Current demo

- Android camera scanning through Google ML Kit Document Scanner in basic crop/rotate mode, producing a PDF. Its gallery entry remains available; Single Document also imports local JPG/PNG bytes directly through Open file. Scanner components may need initial preparation through Google Play services.
- Bundled Latin-script ML Kit OCR for local PDF/JPG/PNG processing.
- One selected local PDF/JPG/PNG or camera scan at a time. Input working copies are private; shared output remains restricted to internal local device storage.
- A remembered Single Document output location, an empty document area after saving or backgrounding, on-demand fullscreen preview, ZoomImage 1.3.0 zoom/pan controls, PDF page navigation, editable name, OCR evidence, and a save confirmation dialog with an Edit name shortcut. Confirmation is not restored automatically.
- Exact byte-hash duplicate checks under the selected output root, including old export subfolders. Existing verified content blocks OCR and another save, even after reopening, with **Document already renamed**. Missing completed copies continue normally after successful inspection; uncertain exports require inspection and access/read errors block continuation.
- Export original bytes directly into the selected output folder. No extra subfolder is created; old export subfolders remain covered by duplicate checks. Never rename, write, or delete imported originals. Duplicate output names receive `(1)`, `(2)`, etc.
- SHA-256 snapshots, bounded streaming copy, output verification, and a durable operation journal. Full batch interruption recovery remains planned.
- Optional XLSX company-list import is parsed locally and stored app-privately. Matching uses list entries first, then SEC certificate patterns; suggestions remain editable and require user approval.
- Batch mode uses local source/output folders, processes up to 10 pending files concurrently for review, and saves each approved document separately. The code currently accepts folders with up to 10,000 supported files; performance beyond tested samples is not established.
- PSBank-inspired working blue/red theme. Exact official color tokens have not been verified.

This is a demo for fictional or approved test documents, not a production bank release. Background processing, restoration of in-progress OCR/name edits, validation of the accepted 200-file capacity, Windows packaging, and any iPadOS target remain future work. The accepted product requirements and current implementation gaps are in [BATCH_PROCESSING_PLAN.md](./BATCH_PROCESSING_PLAN.md).

## Build

The repository includes a Gradle 8.11.1 wrapper with its matching distribution checksum. Build configuration uses JDK 17, Android SDK 35, AGP 8.9.2, Kotlin 2.1.20, and Compose Multiplatform 1.8.2. The local compile check passed with this toolchain.

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
- Camera scans are private working PDFs. New camera scans are removed after verified renamed-copy export and a final source-hash check; failed/unexported scans and older retained captures remain. Unused imported working copies are cleaned up after retirement unless an unresolved export protects them. Originals, renamed output files, and journals are preserved. The scanner does not provide untouched sensor photos through this workflow.
- A successful export preserves source bytes and verifies output contents. Local journals are not tamper-proof audit records. Output-folder access controls and independent backups remain bank responsibilities.
- Only tested local document providers should be supported. The demo rejects unsupported cloud/third-party providers and checks source/output separation where the original folder identity is available. Storage-provider publication is not claimed atomic under arbitrary concurrent external changes.
- Local JPG/PNG decoding applies EXIF orientation. Automated deskew remains limited; poor or tilted scans can require manual correction/review. Physical-device image-orientation tests remain necessary.

Product scope and decisions: [APP_SPECIFICATION.md](./APP_SPECIFICATION.md).

## Local architecture graph

The optional Graphify workflow builds a code-only graph and an Obsidian vault locally. Generated output is kept in ignored `.tools/` so demo PDFs, Excel lists, and other document contents are not indexed or committed. Install Graphify 0.9.104 with `uv tool install graphifyy==0.9.104`, then refresh the graph with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Update-ProjectGraph.ps1
```

Open `.tools/secrename-knowledge-vault` as a vault in Obsidian; `graph.canvas` shows the component map. The graph is a navigation aid, so confirm findings against source before changing architecture.

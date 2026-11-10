# SEC Rename testing strategy

## Goal

Catch document matching, duplicate tracking, file handling, and user-flow regressions while keeping test documents local. Automated tests should use synthetic or approved demo documents only. Do not place real bank documents in a shared CI service or upload them to a test provider.

## Test layers

| Layer | What it verifies | Suggested tooling | When to run |
| --- | --- | --- | --- |
| Logic/unit | Company-list parsing and matching, OCR text normalization, ambiguity rules, filename validation, SHA-256 identity, batch ordering/refill, and missing-output recovery | Existing Kotlin/JUnit tests | Every code change |
| Component/integration | OCR result to proposed name, batch state to row UI, duplicate history to skip/retry behavior, and save/copy error handling | Kotlin tests with fakes; AndroidX Test for Android APIs | Before merging a feature |
| UI smoke/flow | App launches, Batch mode opens, and its start action is present; later flows can cover source/destination selection and review/save | Maestro locally against an Android emulator or device | Before a demo or release |
| Device/release | Camera, Android document/folder pickers, offline operation, screen sizes, process restart, and release build behavior | Manual checks plus AndroidX instrumented tests on emulator and phone | Before handing the app to a pilot user |

## Current baseline

The project already has Kotlin/JUnit logic coverage, including batch ordering, content-hash duplicate recognition, and the case where a previously exported copy is missing. Keep these deterministic tests as the main regression suite. UI automation complements them; it does not replace real-device checks for the camera and Android storage pickers.

## Local UI smoke test

The `.maestro/batch-smoke.yaml` flow is a local smoke-test starting point. Install the debug app on the emulator/device, install the open-source Maestro CLI, then run from the repository root:

```powershell
maestro test .maestro/batch-smoke.yaml
```

The test interacts with the app UI on the selected device. Use demo inputs only. Keep OCR fixtures and exports in local emulator storage; avoid cloud-device test services for sensitive material.

## Coverage checklist for the batch feature

- Files are processed in natural ascending filename order, including names such as `file2` and `file10`.
- A batch presents at most ten pending documents and refills a free slot after a successful save or a confirmed already-processed match.
- A hash match in the chosen output folder is skipped even if the source filename differs.
- If the previous output copy is missing, the source document becomes eligible again.
- A name match that is ambiguous remains unresolved and editable; it must not be saved automatically.
- One unresolved row does not prevent other reviewed rows from being saved.
- A failed copy does not mark the source as completed.
- Reopening the app does not lose the established batch progress or incorrectly mark an unexported document as complete.
- Offline smoke check: disable Wi-Fi and mobile data, then run OCR on a local demo file and save to local storage.

## Security boundary

Test the app with synthetic/demo documents. Keep automated execution local to the developer's emulator/device unless a bank-approved CI environment is established. Test logs and screenshots should not contain real company or customer data.

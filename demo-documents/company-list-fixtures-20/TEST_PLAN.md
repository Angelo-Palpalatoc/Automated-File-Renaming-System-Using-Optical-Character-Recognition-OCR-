# Company-list milestone test plan

The 20 fictional PDFs and the 20-name Excel list in this folder are a controlled evaluation set for the planned Single Document company-list feature. Record the actual name proposed by the app for each case in `TEST_CASES.md` and compare it with the expected result. The current demo has not implemented company-list import yet, so these are future acceptance cases.

## Testing sequence

1. Import the Excel list. Confirm that the preview shows 20 usable names and that the selected list remains available after closing and reopening the app.
2. Open cases 01-18 one at a time. Compare the editable filename field and any inline message with `TEST_CASES.md`. Do not save cases 11, 13, or 14 until a user enters a verified name.
3. Save case 01, then open case 19 in the same output location. The exact duplicate must be blocked. Open case 20 and confirm that a distinct document from the same company can be reviewed and saved with a collision-safe filename.
4. Replace the list with an updated workbook during a separate import test. Confirm the replacement preview before activation. Check that the prior list remains available if import is canceled or fails.
5. Repeat representative cases in airplane mode after scanner setup. Confirm local OCR, list lookup, review, and verified export. Test a camera scan on the S23 Ultra using a printed fixture or approved non-sensitive document.

## Failure and boundary checks

- Import an empty workbook, one with a missing Company Name column, duplicate names, an unreadable file, and a very large workbook. Reject or explain each failure without discarding the last valid list.
- Check punctuation and case changes separately from one-letter and two-letter changes. A close match fills an editable suggestion only when it is unique. Case 11 leaves the filename empty and shows the inline ambiguity message.
- Verify that the list match comes from the certificate holder area. Case 05 mentions a bank in the resolution and case 15 mentions an unrelated supplier.
- Change the suggested name manually, confirm the final filename, and check that this document-level edit does not alter the imported list.
- In case 04, notice the intentional CORPRATION typo in the Excel list. Verify the user can correct the filename to CORPORATION after checking the document.
- Revoke output-folder access, fill storage, rotate the device, and background the app during import or review. Confirm that no partial import becomes active and no incomplete output is reported as saved.
- Check the UI on the S23 Ultra and the client tablet when available, including larger text and touch accessibility.

## Evaluation record

Record for every case the extracted name, list match, suggested filename, whether review was required, and final save result. Count separately: correct clear suggestions, correct review requests, wrong clear suggestions, and missed companies. A wrong clear suggestion is the most important failure to investigate, even though the user confirms before saving. Keep the original input and output hashes when checking copy integrity.

## Test tooling selection

- Keep the existing Kotlin/JUnit tests for matching and workbook validation logic. They run quickly on the development machine.
- Use the official AndroidX Compose testing APIs for a small number of critical UI flows, such as import preview, empty ambiguous result, manual edit, and save confirmation. Add this dependency only when implementing those tests.
- Robolectric is an optional later tool for Android-specific logic that needs a simulated Android runtime. It is unnecessary for pure Kotlin matching rules.
- Run a short emulator workflow for file picking and output storage, then use the physical S23 Ultra for camera and real-device behavior.

Android's testing guidance favors small tests for logic, device tests for platform interactions, and explicit edge cases. The AndroidX Test repository is maintained by the Android organization and its published source uses Apache 2.0. Robolectric is a maintained, documented open-source project, but any added version and dependencies need review before use.

References: [Android testing strategy](https://developer.android.com/training/testing/fundamentals/strategies), [what to test](https://developer.android.com/training/testing/fundamentals/what-to-test), [Compose UI tests](https://developer.android.com/develop/ui/compose/testing), [AndroidX Test repository](https://github.com/android/android-test), [Robolectric repository](https://github.com/robolectric/robolectric).

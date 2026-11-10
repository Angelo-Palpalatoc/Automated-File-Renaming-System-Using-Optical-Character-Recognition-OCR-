# Folder batch processing — plan for review

Status: the foreground batch queue and optional XLSX company-list import are implemented in the Android demo. This document records the approved target scope and remaining validation/recovery work; it is not a statement that the whole plan has shipped.

## Current implementation snapshot (2026-11-10)

- Batch mode selects separate local source and output folders, optionally imports an XLSX company list, and presents the first ten pending supported files in natural filename order.
- Those ten files are analyzed concurrently. Each row has a clickable filename for preview, an editable company field, and an individual Save action. A saved or already-processed row leaves the pending set and another pending file fills its slot.
- Saved/skipped file identities are remembered for the same source/output pair and reconciled against destination content when the queue is rebuilt. Pending OCR results and unsaved name edits are not restored after process death.
- The implementation currently allows up to 10,000 supported entries in the source folder. The accepted product target is 200; test 20, 50, 100, and 200 before enforcing or advertising that capacity.
- Background processing, pause/resume of active analysis, durable restoration of review edits, and physical-device performance validation remain incomplete.

## Decisions accepted on 2026-10-31

- Deliver the cleaner Single Document UI, local fullscreen viewer, remembered output, and persistent duplicate protection first.
- Detect exact duplicate content in the selected output location before OCR where possible. Hashing reads bytes locally and does not require OCR. Verify actual destination content, including after an app restart; company names alone do not identify duplicate documents. Rescanned or recompressed documents can have different hashes.
- If destination inspection succeeds and no matching copy remains, allow normal reading/review/re-export without a missing-copy warning or extra recovery confirmation. Keep prior export records. Uncertain publication still requires output inspection.
- Batch review will use a compact filename row that opens a document viewer and an explicit approval action, with Approve & next navigation. Do not show all previews by default or approve files automatically.
- Retain the proposed 200-file upper limit; validate smaller batches first, including 100 files. Show ten-document review milestones to make progress manageable; each document still requires approval.
- Foreground batch processing with Pause/Continue and durable recovery after interruption. A crash may leave an uncertain output: reconcile it before resuming that item rather than blindly creating another copy.
- Remember output locations separately per mode and restore them after reopening. Always display the destination and provide Change; recover expired permissions explicitly.

## Current behavior and proposed capacity

The Android demo now includes a foreground batch queue alongside Single Document. The queue lists supported files and reviews up to ten pending files at a time; this does not establish that a 200-file batch has been validated on the phone or client tablet.

Proposed first batch release: **up to 200 selected files per batch**. This is a product limit for validation, not an OCR engine maximum or a guaranteed processing speed. Start testing with 20 files, then 50, 100, and 200. Processing time, memory, temperature, and storage requirements must be measured on the S23 Ultra and the eventual client tablet.

Retain the existing per-file limits initially: supported PDF/JPG/PNG, PDFs of 1–30 pages, and a 200 MiB file-size ceiling. One multipage PDF counts as one file and receives one output filename. Each file keeps its original format and bytes when exported. Scanner-generated PDFs remain separate processed scan sources, as in the current app.

## Proposed workflow

1. **Choose source and output.** Keep the source unchanged and save renamed copies directly into the user-selected output folder.
2. **Prepare the batch.** Show supported, unsupported, oversized, and selected file counts. Allow Select all supported files or individual selection. Subfolders stay excluded initially. Above 200 files, show the full count and require a selection of up to 200; never silently imply that the whole folder was processed. Remaining files can be selected for a later batch.
3. **Start reading.** OCR each selected document sequentially. Show file progress, page progress, current filename, and readable/review/failed counts. Do not invent time estimates or confidence percentages.
4. **Review the suggested names.** Use the batch review list and document preview described below. A suggestion is evidence for review, not authorization to export.
5. **Export approved files.** A single action saves every approved item, sequentially, into this batch's output folder. Unreviewed and failed items stay pending. Export can be partial and the batch reopened later.
6. **Show a durable summary.** Report completed, awaiting review, skipped, failed, and requiring output inspection. Provide the actual saved filenames and batch location.

## How review works across many files

The app reads the selected files automatically, then presents one batch review screen. The operator does not repeat folder selection or manually start OCR for each document.

Example after reading three files:

| Original filename | Suggested company | Review state | Action |
| --- | --- | --- | --- |
| SEC_Demo_01.pdf | SILVERBAY MERIDIAN TRADING CORPORATION | Awaiting review | Open preview |
| SEC_Demo_02.pdf | NORTHVALE INFRASTRUCTURE SOLUTIONS INC. | Awaiting review | Open preview |
| needs-review.pdf | No reliable suggestion | Needs manual entry | Open preview and edit |

Selecting a row opens its preview and review panel. On a wide tablet, keep the list on the left and preview/company fields on the right. On a phone, open a review page with Previous and Next navigation and an item counter.

The review panel shows:

- Original filename, current suggested company, and proposed output filename.
- Page navigation and zoom so the operator can check the certificate holder, including later pages. Improve the current first-page-only preview before treating multipage review as complete.
- Editable company field and optional OCR evidence; multiple candidates remain clearly marked for selection.
- **Approve & next** to record the verified name and advance to the next unresolved item.
- **Skip for now** to leave an uncertain document pending without blocking others.
- A remaining-review counter, for example **12 of 50 approved**.

Back in the batch list, the operator can filter All, Awaiting review, Approved, Failed, and Saved. **Export approved files (12)** saves the 12 approved items in one operation and leaves the others pending. Name edits, changed source content, or re-analysis invalidate the affected approval. Approval belongs to that document's content hash and reviewed company name, not to its row position.

This accepted first-version policy requires a person to verify each exported company's name. The efficiency gain is automatic reading of the folder, quick in-app preview/approval, and one export action for all approved documents. It does not remove human review. Group progress into milestones of ten reviewed documents without hiding remaining items or bulk-approving them.

The pending company list can later help flag matches, spelling differences, and unknown companies. A list match alone does not prove that the app selected the certificate holder rather than another company mentioned in the document. Do not automatically mark matches approved.

## Filenames, duplicate companies, and source preservation

- Create renamed copies; do not rename, delete, or write to imported originals.
- Different documents for the same company receive `COMPANY.pdf`, `COMPANY (1).pdf`, and so on, respecting existing destination names without overwriting them.
- Use a stable source order and persist each assigned output name. A resumed batch retains known assignments and completed outputs.
- Detect source changes before export and require re-analysis and review of the changed file.
- Identical content hashes can be flagged as possible duplicate documents. Do not silently discard documents just because hashes or company names match.
- Multiple company candidates within one document require review of the certificate holder; do not split or merge documents automatically.
- Verify the copied bytes before reporting completion. If another process creates a conflicting output name, resolve it without overwriting; show the actual final filename in the result.

## Progress, errors, pausing, and recovery

Show two distinct phases: **Reading documents** and **Exporting approved copies**. For example: `Reading 12 of 50 · page 2 of 3`, then `Saving 8 of 12 approved copies · verifying content`. Review progress is separate from OCR and export progress.

Use clear item states: Queued, Reading, Awaiting review, Approved, Copying, Verifying, Saved, Failed, Skipped, and Check output. Batch-level states include Reading, Ready for review, Exporting, Paused, and Completed with issues.

| Situation | Planned response |
| --- | --- |
| Corrupt, protected, oversized, or unsupported document | Mark that item with an actionable reason and continue to the next readable file |
| No text, no reliable candidate, or multiple candidates | Hold for manual review; never export under an empty or guessed name |
| Output access lost, output unavailable, or storage full | Pause the export queue; preserve completed results and remaining work |
| User taps Pause | Finish the current file's safe operation, then pause; explain that the current file may take time |
| User stops a batch | Stop starting new work; retain originals, completed copies, approvals, and history |
| App closes, crashes, or Android stops it | Reopen with a recovery prompt; reconcile recorded output state before offering Resume |
| Publication or verification uncertain | Mark Check output; do not automatically export that item again |
| Completed item encountered after restart | Verify the recorded destination and hash; do not generate another numbered copy blindly |

For the first implementation, keep active processing in the foreground and clearly ask users to keep the app open. Do not promise uninterrupted screen-off/background processing. Durable state allows explicit recovery after interruption; background execution is a separate milestone.

Preflight storage against the selected source sizes plus a reserve for app data and rendering. File count alone is not a sufficient capacity check. If a provider cannot report reliable free-space information, explain the uncertainty and handle write failures without labelling an incomplete output successful.

## Kotlin Multiplatform architecture

Retain Kotlin Multiplatform + Compose Multiplatform; no handwritten Java is needed.

| Layer | Planned responsibility |
| --- | --- |
| Shared Kotlin domain | Batch/item models, approval rules, state transitions, queue ordering, naming, summary counts, and recovery decisions |
| Shared Compose UI | Batch setup, progress, searchable/filterable review list, preview controls, approved-file export, and summary/history |
| Android adapters | Existing bundled OCR, PDF/image rendering, selected-folder access, hashing/copy verification, and local durable storage |
| Windows adapters later | Local OCR, rendering, file access, and persistence behind the same interfaces |

Extend the existing per-export journal with a durable batch manifest and item IDs. Commit transitions atomically and retain stable batch ID, source identity/hash, reviewed name, approval state, destination identity/hash, assigned filename, and result state. Keep persistence behind a platform interface; the existing Android AtomicFile approach can support the initial bounded queue. Reassess a local database if scale/query needs justify it. Full document images and OCR text should not enter routine logs.

The runner must own processing independently of Compose recomposition. Allow only one active runner and one export writer per batch. Explicitly re-check persisted state on resume. Never claim exactly-once export where the document provider left publication uncertain.

## Security and compatibility

- Process documents locally with existing app network-permission exclusions and backup restrictions.
- Keep batch metadata in app-private storage and show in-app notices; do not put company names in lock-screen notifications.
- Retain screenshot protection and selected-folder access. Initial batch input/output stays on supported internal local device storage.
- Company-list import stays optional; batch processing must work before the list arrives.
- Original preservation is not an independent backup. Keep the existing warning for camera captures that exist only inside app storage.
- Resume must handle revoked permissions and missing/changed sources. Never scan additional folders or access cloud providers silently.

## Implementation sequence and acceptance

1. **Queue and state:** persistent batch/item identities, sequential runner, per-item errors, and pause/stop boundaries.
2. **Reading and review:** folder preflight, selection/limits, complete batch OCR, progress, filters, editable review, multipage preview, and Approve & next.
3. **Bulk export:** approved-item export, deterministic duplicate handling, checksums, receipts, and summary.
4. **Recovery:** reconcile incomplete journals and recorded outputs, prevent duplicate saves on resume, and recover folder access.
5. **Validation:** mixed valid/corrupt/no-name/multiple-name/repeated-company files; changed sources; duplicate taps; low space; permission loss; interruption during copy/publication; and reopening completed batches. Test 20, 50, and 200 files, plus explicit selection from a folder containing more than 200.

Acceptance requires unchanged source names/hashes, matching completed output hashes, no overwrites, no automatic export of unreviewed items, accurate result counts, and no blind retry of uncertain outputs. Measure elapsed time and memory on actual target hardware before advertising throughput.

## Accepted scope for the later batch release

- Capacity: up to 200 files per batch, sequential processing; validate 20, 50, 100, and 200 before claiming device support.
- Review policy: read all automatically, open a filename to view the document, approve each item, then export all approved files together. Automatic eligibility/export is deferred.
- Scope: folder-based batches after Single Document validation. Combining many camera captures into a batch can follow.
- First-release operation: foreground processing with durable explicit pause/resume and restart reconciliation.

See [APP_SPECIFICATION.md](./APP_SPECIFICATION.md) for the broader product decisions and [DEMO_GUIDE.md](./DEMO_GUIDE.md) for the currently implemented demo.

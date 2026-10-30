# SEC-style scanned certificate test dataset (synthetic)

10 one-page, image-only PDF files built for an offline document classifier / OCR workflow.

**IMPORTANT:** The files are entirely fictional. They are NOT certificates issued by the Philippine Securities and Exchange Commission. They have conspicuous invalidity banners/watermarks, fictional company names and fake `TEST-SEC-...` IDs, and no official seal, official SEC logo, valid QR code, government signature, or other real security feature. DO NOT use them to support real bank verification, company registration, or legal proceedings.

## Coverage

- 10 individual PDF files (`SEC_Demo_01.pdf` through `SEC_Demo_10.pdf`)
- **8 distinct fictional companies:** files 01-08
- **2 repeats:** file 09 has the same company identity and simulated registration ID as file 02; file 10 matches file 07
- Variations: clean scans, warm/low-contrast scanning, grayscale, JPEG compression, light skew, and blur
- Embedded pages are *raster images*: normal PDF text extraction returns empty; run offline OCR (Tesseract / PaddleOCR, etc.) to extract text
- `expected_results.csv` and `expected_results.json` are ground-truth labels for testing company-name extraction, filing and duplicate handling
- `contact_sheet.png` is a 10-page thumbnail preview

## Recommended acceptance criteria

1. Detect document type `Certificate of Incorporation`.
2. Detect the company's name from the prominent centered line (not from issuer name).
3. Extract the synthetic `TEST-SEC-*` registration ID, and associate it with the company.
4. **Do not replace existing files**: add a document ID or upload timestamp, e.g. `C002_NORTHVALE_INFRASTRUCTURE_SOLUTIONS_INC__DOC0009.pdf`.
5. Preserve the original scan and write a record of the recognition, OCR confidence, timestamp, source, and output name.
6. Handle `SEC_Demo_09.pdf` and `SEC_Demo_10.pdf` as repeated *company identities*; do not merge or overwrite automatically.
7. Route uncertain matches to human review; validate real documents independently against SEC records.
8. Process data locally; encrypt stored files and restrict access according to your bank's policies.

## Reference (structure only)

Official Philippine SEC eSPARC example certificate (used as a layout reference, not reproduced):
https://esparc.sec.gov.ph/application-one-sec/prev-cert?guid=d357a1a0-7e1c-4b27-bbd1-8c8ec0a7ba83

These PDF files emulate the layout concepts: government-heading placement, registration-number field, centered certificate heading, prominently centered corporation name, prose clauses, and issuance-area placement. They are deliberately differentiated and are not pixel-identical replicas of an authentic certificate.

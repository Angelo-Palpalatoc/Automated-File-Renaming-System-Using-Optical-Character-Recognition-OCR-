# Synthetic certificate regression fixtures

The adjacent text files are local Windows.Media.Ocr transcripts of all ten image-only PDFs in the user-provided `PSBank_SEC_Demo_Certificates_10` synthetic dataset. Their README explicitly labels every company/document fictional and invalid for official use.

Pages were rendered at a 2000-pixel long edge using Windows.Data.Pdf before local Windows OCR. `expected.tsv` contains the dataset's expected company-name labels. Extraction tests read these fixtures; production extraction does not read expected labels or hard-code company names.

These fixtures establish name-extraction behavior on independently recognized text. They are not Android ML Kit OCR results and do not establish the updated APK's accuracy on the physical phone. The Android OCR evidence should be checked separately if a sample is still missed.

`secretary-philippine.txt` and `secretary-sec.txt` are minimal OCR excerpts from the two user-provided Sample PNGs. They retain the document heading, company/secretary relationship, and the later bank mention as a negative example. Personal identification, signatures, and notarial details are excluded. The expected holders are SUNRIDGE INDUSTRIES INC. and ABC SOLUTIONS INC.; the banks mentioned in their resolutions are not the certificate holders.

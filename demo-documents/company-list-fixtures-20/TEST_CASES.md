# Company matching fixture set

All names and registration numbers are fictional. These files are not SEC certificates.
Import sample_company_list_20.xlsx, then open each PDF. Use a separate output folder.

| PDF | Expected proposed name | Purpose |
| --- | --- | --- |
| case_01.pdf | ABC SOLUTIONS INC. | Exact incorporation match |
| case_02.pdf | NORTHVALE INFRASTRUCTURE SOLUTIONS INC. | Long name |
| case_03.pdf | CEDARSTONE FOOD DISTRIBUTION CORPORATION | Close list neighbor must not win |
| case_04.pdf | WESTBROOK GENERAL MERCHANDISE CORPRATION | Intentional Excel typo; user corrects suggestion |
| case_05.pdf | ABC SOLUTIONS INC. | Bank named in resolution is not holder |
| case_06.pdf | SUNRIDGE INDUSTRIES INC. | Secretary role identifies company |
| case_07.pdf | HARBORLIGHT AGRI SUPPLY CORPORATION | First clause identifies company |
| case_08.pdf | EVERGREEN OFFICE SUPPLIES INC. | Corporate Name field |
| case_09.pdf | ABC SOLUTIONS INC. | Missing S needs review |
| case_10.pdf | SUMMITBROOK TECHNOLOGY SERVICES INC. | OCR-like wrong letter needs review |
| case_11.pdf | (empty or duplicate blocked) | Competing list matches, leave name empty |
| case_12.pdf | WINDWARD EQUIPMENT CORPORATION | Unlisted company uses document fallback |
| case_13.pdf | (empty or duplicate blocked) | No company, leave name empty |
| case_14.pdf | (empty or duplicate blocked) | Bank mention alone, leave name empty |
| case_15.pdf | BLUECREST LOGISTICS CORPORATION | Unrelated supplier mentioned below holder |
| case_16.pdf | GOLDENFIELD AGRICULTURAL TRADING CORPORATION | Holder appears on PDF page two |
| case_17.pdf | HARBORPOINT CONSTRUCTION SERVICES INC. | Holder split across lines |
| case_18.pdf | MAPLESTONE BUSINESS SOLUTIONS INC. | Case and punctuation variation |
| case_19.pdf | (empty or duplicate blocked) | Exact byte duplicate of case 01 |
| case_20.pdf | ABC SOLUTIONS INC. | Same company, distinct document |

Cases 09 and 10 require user review. Case 11 requires a manual choice.
The list intentionally misspells CORPRATION in row 21. In case 04 the document reads CORPORATION. Review and correct the filename before saving.
Case 19 has exactly the same bytes as case 01. Case 20 has the same company but different bytes.
The expected results describe the planned company-list feature, which is not yet implemented.
These PDFs are rendered before OCR in the Android app. They do not simulate camera glare, blur, or printer defects.

package com.local.secrename.shared

/** UI-only summary: document processing and workbook I/O stay in platform adapters. */
data class CompanyListState(
    val filename: String = "",
    val count: Int = 0,
    val preview: CompanyListPreview? = null,
)
data class CompanyListPreview(
    val filename: String,
    val sheets: List<String>,
    val sheetIndex: Int,
    val columns: List<String>,
    val columnIndex: Int,
    val names: List<String>,
    val count: Int,
    val note: String = "",
    val error: String? = null,
)

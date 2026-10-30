package com.local.secrename.shared

import androidx.compose.ui.graphics.ImageBitmap

enum class NoticeKind { INFO, SUCCESS, WARNING, ERROR }
enum class NextAction(val label: String) {
    NONE(""), SOURCE("Choose source folder"), OUTPUT("Choose output location"),
    ANALYZE("Read company name"), REVIEW("Review company name"), SCAN("Try scanner again"), HISTORY("Check export history"),
}
data class UserNotice(
    val id: Long = 0,
    val kind: NoticeKind = NoticeKind.INFO,
    val title: String = "Ready when you are",
    val detail: String = "Scan a document or choose a folder to get started.",
    val action: NextAction = NextAction.NONE,
    val announce: Boolean = false,
)
data class DocumentItem(val id: String, val name: String)
data class SavedCopy(val name: String, val batch: String)
data class ExportHistoryItem(val source: String, val target: String, val date: String, val completed: Boolean)

data class DemoState(
    val busy: Boolean = false,
    val operation: String = "",
    val progress: Float? = null,
    val notice: UserNotice = UserNotice(),
    val sourceLabel: String = "No source selected",
    val outputLabel: String = "No output selected",
    val outputReady: Boolean = false,
    val files: List<DocumentItem> = emptyList(),
    val selectedId: String? = null,
    val company: String = "",
    val evidence: String = "",
    val candidates: List<String> = emptyList(),
    val proposal: String = "",
    val nameError: String? = null,
    val analyzed: Boolean = false,
    val verifiedByUser: Boolean = false,
    val preview: ImageBitmap? = null,
    val savedCopy: SavedCopy? = null,
    val exportNeedsInspection: Boolean = false,
    val history: List<ExportHistoryItem> = emptyList(),
    val historyLoaded: Boolean = false,
) {
    val exportBlocker: String?
        get() = when {
            busy -> "Please wait for the current operation to finish."
            exportNeedsInspection -> "Check export history and the output folder before exporting again."
            savedCopy != null -> "This reviewed copy has been saved. Select another document to continue."
            selectedId == null -> "Select a document first."
            !analyzed -> "Read the document before saving a copy."
            nameError != null -> nameError
            company.isBlank() || proposal.isBlank() -> "Enter the company name shown on the document."
            !verifiedByUser -> "Confirm that you checked the company name against the document."
            !outputReady -> "Choose a separate output location."
            else -> null
        }
    val canExport: Boolean get() = exportBlocker == null
}

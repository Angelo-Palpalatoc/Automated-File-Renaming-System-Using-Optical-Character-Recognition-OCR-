package com.local.secrename.shared

import androidx.compose.ui.graphics.ImageBitmap

enum class NoticeKind { INFO, SUCCESS, WARNING, ERROR }
enum class AppScreen { HOME, SINGLE, BATCH }
enum class NextAction(val label: String) {
    NONE(""), SOURCE("Choose source folder"), OUTPUT("Choose output location"),
    ANALYZE("Read company name"), REVIEW("Review company name"), SCAN("Try scanner again"), HISTORY("Check export history"), CHECK_FILE("Check file"),
}
data class UserNotice(
    val id: Long = 0,
    val kind: NoticeKind = NoticeKind.INFO,
    val title: String = "Ready when you are",
    val detail: String = "Scan a document or choose a folder to get started.",
    val action: NextAction = NextAction.NONE,
    val announce: Boolean = false,
    val savedCopy: SavedCopy? = null,
)
data class DocumentItem(val id: String, val name: String, val mime: String = "")
data class SavedCopy(val name: String, val folder: String, val uri: String = "", val folderUri: String = "")

/** Only verified saves use a banner. Issues stay inline; routine progress stays quiet. */
object NoticePolicy {
    fun shouldAnnounce(kind: NoticeKind, action: NextAction, savedCopy: SavedCopy?): Boolean =
        kind == NoticeKind.SUCCESS && action == NextAction.CHECK_FILE && savedCopy != null && savedCopy.folderUri.isNotBlank()

    fun shouldDisplayInline(kind: NoticeKind): Boolean = kind == NoticeKind.WARNING || kind == NoticeKind.ERROR
}
data class ExportHistoryItem(
    val source: String, val target: String, val date: String, val completed: Boolean, val savedCopy: SavedCopy? = null,
    val id: String = "", val exportedAt: Long = 0, val dayKey: String = "unknown", val dayLabel: String = "Date unavailable",
) {
    // Navigation belongs to this record, independent of the current output setting.
    val checkFileCopy: SavedCopy? get() = savedCopy?.takeIf { completed && it.folderUri.isNotBlank() }
}

data class ExportHistoryDay(val key: String, val label: String, val items: List<ExportHistoryItem>)

object ExportHistory {
    const val PAGE_SIZE = 50
    fun ordered(items: List<ExportHistoryItem>): List<ExportHistoryItem> =
        items.sortedWith(compareByDescending<ExportHistoryItem> { it.exportedAt }.thenByDescending { it.id })
    fun days(items: List<ExportHistoryItem>): List<ExportHistoryDay> =
        ordered(items).groupBy { it.dayKey }.map { (key, entries) -> ExportHistoryDay(key, entries.first().dayLabel, entries) }
}
data class DocumentViewer(
    val open: Boolean = false, val busy: Boolean = false, val page: Int = 0,
    val pages: Int = 1, val image: ImageBitmap? = null, val error: String? = null,
    val highDetail: Boolean = false,
)
data class OutputCheck(val match: SavedCopy? = null, val missingPreviousCopy: Boolean = false, val needsInspection: Boolean = false) {
    // Completed history alone does not prevent replacing a copy that is no longer present.
    val canProceed: Boolean get() = match == null && !needsInspection
}

/** A one-time UI review of this exact document, name, and destination. Never persisted. */
data class SaveConfirmation(val documentId: String, val company: String, val filename: String, val outputId: String, val revision: Long)

/** Evidence must be scoped to the chosen output location by its platform adapter. */
object DuplicateGuard {
    fun evaluate(hash: String, outputs: Sequence<Pair<String, SavedCopy>>, completedBefore: Boolean, uncertainBefore: Boolean): OutputCheck {
        val match = outputs.firstOrNull { it.first == hash }?.second
        return OutputCheck(match, missingPreviousCopy = match == null && completedBefore && !uncertainBefore, needsInspection = match == null && uncertainBefore)
    }
}

data class DemoState(
    val screen: AppScreen = AppScreen.HOME,
    val busy: Boolean = false,
    val operation: String = "",
    val progress: Float? = null,
    val notice: UserNotice = UserNotice(),
    val sourceLabel: String = "No source selected",
    val outputLabel: String = "No output selected",
    val outputReady: Boolean = false,
    val outputId: String = "",
    val inputRequest: InputRequest? = null,
    val companyList: CompanyListState = CompanyListState(),
    val batchSourceLabel: String = "No source selected",
    val batchOutputLabel: String = "No output selected",
    val batchQueue: BatchQueue? = null,
    val batchStarted: Boolean = false,
    val batchReviews: Map<String, BatchReview> = emptyMap(),
    val batchConfirmId: String? = null,
    val matchIssue: String? = null,
    val reviewRevision: Long = 0,
    val files: List<DocumentItem> = emptyList(),
    val selectedId: String? = null,
    val company: String = "",
    val evidence: String = "",
    val candidates: List<String> = emptyList(),
    val proposal: String = "",
    val nameError: String? = null,
    val analyzed: Boolean = false,
    val preview: ImageBitmap? = null,
    val savedCopy: SavedCopy? = null,
    val exportNeedsInspection: Boolean = false,
    val history: List<ExportHistoryItem> = emptyList(),
    val historyLoaded: Boolean = false,
    val historyHasMore: Boolean = false,
    val duplicateChecked: Boolean = false,
    val duplicate: SavedCopy? = null,
    val viewer: DocumentViewer = DocumentViewer(),
) {
    /** Clear only the current document; keep destination settings, receipts, and active I/O status. */
    fun clearDocument(): DemoState = copy(
        inputRequest = null,
        reviewRevision = reviewRevision + 1, sourceLabel = "No source selected",
        files = emptyList(), selectedId = null, company = "", evidence = "", candidates = emptyList(),
        proposal = "", nameError = null, matchIssue = null, analyzed = false, preview = null, savedCopy = null,
        exportNeedsInspection = false, duplicateChecked = false, duplicate = null,
        viewer = DocumentViewer(),
    )

    val canAnalyze: Boolean get() = !busy && selectedId != null && outputReady && duplicateChecked && duplicate == null && !exportNeedsInspection
    val exportBlocker: String?
        get() = when {
            busy -> "Please wait for the current operation to finish."
            duplicate != null -> "Document already renamed."
            exportNeedsInspection -> "Check export history and the output folder before exporting again."
            savedCopy != null -> "This reviewed copy has been saved. Select another document to continue."
            selectedId == null -> "Select a document first."
            !analyzed -> "Read the document before saving a copy."
            nameError != null -> nameError
            company.isBlank() || proposal.isBlank() -> "Enter the company name shown on the document."
            !outputReady || outputId.isBlank() -> "Choose a separate output location."
            !duplicateChecked -> "Check for existing copies in the output location first."
            else -> null
        }
    val canExport: Boolean get() = exportBlocker == null

    fun saveConfirmation(): SaveConfirmation? = if (canExport) {
        SaveConfirmation(requireNotNull(selectedId), company, proposal, outputId, reviewRevision)
    } else null

    fun acceptsConfirmation(confirmation: SaveConfirmation): Boolean = saveConfirmation() == confirmation
}

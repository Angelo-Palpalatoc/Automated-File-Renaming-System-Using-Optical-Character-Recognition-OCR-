package com.local.secrename

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.secrename.shared.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeoutException

class DemoViewModel(app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(DemoState())
    val state = mutable.asStateFlow()
    private val resolver = app.contentResolver
    private val store = ExportStore(app)
    private var sourceTree: Uri? = null
    private var outputTree: Uri? = null
    private var entries = listOf<Documents.Entry>()
    private var analyzedHash: String? = null

    fun notify(kind: NoticeKind, title: String, detail: String, action: NextAction = NextAction.NONE, announce: Boolean = true) {
        mutable.update { it.copy(notice = UserNotice(it.notice.id + 1, kind, title, detail, action, announce)) }
    }

    private fun job(title: String, detail: String, recovery: NextAction, exporting: Boolean = false, block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, operation = title, progress = null) }
        notify(NoticeKind.INFO, title, detail, announce = false)
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                if (exporting) {
                    val history = runCatching { store.history() }.getOrNull()
                    mutable.update { it.copy(exportNeedsInspection = true, history = history ?: it.history, historyLoaded = history != null || it.historyLoaded) }
                    notify(NoticeKind.ERROR, "Export not confirmed", "${friendlyFailure(failure)} Check export history and the output folder before trying again. Imported originals have not been modified.", NextAction.HISTORY)
                } else notify(NoticeKind.ERROR, "Could not finish: ${title.lowercase()}", friendlyFailure(failure), recovery)
            } finally { mutable.update { it.copy(busy = false, operation = "", progress = null) } }
        }
    }

    private fun friendlyFailure(failure: Exception): String = when (failure) {
        is SecurityException -> "Access is unavailable, or this PDF is protected. Choose the folder again, or use an approved unlocked copy."
        is TimeoutException -> "Reading the document took too long. Try again with a clearer or smaller scan."
        is IOException -> "The file could not be read or written. Check local storage space and folder access, then try again."
        is IllegalArgumentException, is IllegalStateException -> failure.message?.take(240) ?: "The document or selected location could not be used. Choose a supported local file and try again."
        else -> "The operation could not be completed. Check the document and folder access, then try again."
    }

    fun pickerCancelled(output: Boolean) = notify(NoticeKind.INFO, "${if (output) "Output" else "Source"} selection cancelled", "Your current document and folder selection are unchanged.", announce = false)

    fun beginScanner(): Boolean {
        if (state.value.busy) return false
        mutable.update { it.copy(busy = true, operation = "Preparing camera and gallery scanner", progress = null) }
        notify(NoticeKind.INFO, "Preparing scanner", "First use may download resources through Google Play services. You can take a photo or use the scanner's gallery button.", announce = false)
        return true
    }

    fun scannerClosed() { mutable.update { it.copy(busy = false, operation = "", progress = null) } }

    fun source(tree: Uri) = job("Opening source folder", "Looking for local PDF, JPG, and PNG documents.", NextAction.SOURCE) {
        store.validateLocal(tree)
        outputTree?.let { store.ensureSeparate(tree, it) }
        val all = Documents(resolver, tree).list()
        val supported = all.filter { it.supported() }.sortedBy { it.name.lowercase() }
        val found = supported.take(200)
        entries = found; sourceTree = tree; analyzedHash = null
        mutable.update { it.copy(sourceLabel = folderLabel(tree), files = found.map { e -> DocumentItem(e.uri.toString(), e.name) }, selectedId = null, analyzed = false, preview = null, evidence = "", company = "", proposal = "", nameError = null, candidates = emptyList(), savedCopy = null, exportNeedsInspection = false, verifiedByUser = false) }
        val skipped = all.size - supported.size
        when {
            found.isEmpty() -> notify(NoticeKind.WARNING, "No supported documents found", "Choose a folder containing PDF, JPG, or PNG files. Subfolders and other file types are skipped.", NextAction.SOURCE)
            supported.size > 200 -> notify(NoticeKind.WARNING, "Showing the first 200 documents", "This pilot lists up to 200 files per folder. Use a smaller source folder to access the others.")
            else -> notify(NoticeKind.SUCCESS, "${found.size} documents ready", "Select a document, then read its company name.${if (skipped > 0) " $skipped unsupported files or subfolders skipped." else ""}")
        }
    }

    fun output(tree: Uri) = job("Checking output location", "Keeping exported copies separate from your source folder.", NextAction.OUTPUT) {
        store.ensureSeparate(sourceTree, tree)
        outputTree = tree
        mutable.update { it.copy(outputLabel = folderLabel(tree), outputReady = true) }
        notify(NoticeKind.SUCCESS, "Output location ready", "Verified copies will be saved in a new batch folder. Existing files will not be overwritten.")
    }

    private fun folderLabel(tree: Uri) = android.provider.DocumentsContract.getTreeDocumentId(tree).replaceFirst("primary:", "Internal storage / ")

    fun select(id: String) {
        if (state.value.busy || entries.none { it.uri.toString() == id }) return
        analyzedHash = null
        mutable.update { it.copy(selectedId = id, analyzed = false, verifiedByUser = false, company = "", evidence = "", proposal = "", nameError = null, candidates = emptyList(), preview = null, savedCopy = null, exportNeedsInspection = false) }
        notify(NoticeKind.INFO, "Document selected", "Read the company name, then check the suggestion against the document.", NextAction.ANALYZE, announce = false)
    }

    fun name(value: String) {
        if (state.value.busy) return
        val entry = selected() ?: return
        val proposal = runCatching { DocumentRules.allocate(value, entry.extension(), emptyList()) }
        mutable.update { it.copy(company = value, proposal = proposal.getOrDefault(""), nameError = proposal.exceptionOrNull()?.message, verifiedByUser = false, savedCopy = if (value == it.company) it.savedCopy else null) }
    }

    fun verify(value: Boolean) { if (!state.value.busy) mutable.update { it.copy(verifiedByUser = value && it.nameError == null && it.company.isNotBlank()) } }
    private fun selected() = entries.find { it.uri.toString() == state.value.selectedId }

    fun analyze() {
        val entry = selected() ?: return
        job("Reading company name", "Reading the document on this device. Keep the app open until the review is ready.", NextAction.ANALYZE) {
            analyzedHash = null
            mutable.update { it.copy(analyzed = false, verifiedByUser = false, company = "", proposal = "", nameError = null, evidence = "", candidates = emptyList(), preview = null, savedCopy = null, exportNeedsInspection = false) }
            val before = store.digest(entry.uri)
            val image = runCatching { preview(entry) }.getOrNull()
            val text = LocalOcr.read(resolver, entry) { completed, total ->
                mutable.update { it.copy(operation = "${completed} of $total pages read", progress = if (total > 0) completed.toFloat() / total else null) }
            }
            mutable.update { it.copy(operation = "Checking the document and company-name candidates", progress = null) }
            check(store.digest(entry.uri) == before) { "The source changed while reading. Read it again before export." }
            analyzedHash = before
            val candidates = DocumentRules.candidates(text)
            val company = candidates.singleOrNull().orEmpty()
            val proposal = runCatching { DocumentRules.allocate(company, entry.extension(), emptyList()) }
            mutable.update { it.copy(analyzed = true, preview = image?.asImageBitmap(), evidence = text, candidates = candidates, company = company, proposal = proposal.getOrDefault(""), nameError = if (company.isEmpty()) null else proposal.exceptionOrNull()?.message) }
            when {
                text.isBlank() -> notify(NoticeKind.WARNING, "No readable text found", "Try a clearer, correctly rotated scan, or enter the company name after checking the original.", NextAction.ANALYZE)
                candidates.isEmpty() -> notify(NoticeKind.WARNING, "Company name needs manual review", "Text was read, but no reliable company-name pattern matched. Check OCR evidence and enter the certificate holder's name.", NextAction.REVIEW)
                candidates.size > 1 -> notify(NoticeKind.WARNING, "${candidates.size} possible company names found", "Choose the certificate holder from the candidates below, or enter the correct name. Bank names and signatories may also appear in the document.", NextAction.REVIEW)
                image == null -> notify(NoticeKind.WARNING, "Name found; preview unavailable", "Check the original with an approved local viewer before confirming this suggestion.", NextAction.REVIEW)
                else -> notify(NoticeKind.SUCCESS, "Company-name suggestion ready", "Compare the suggestion with the certificate holder, then confirm your review. A suggestion is not an accuracy guarantee.", NextAction.REVIEW)
            }
        }
    }

    fun export() {
        val reviewed = state.value
        if (!reviewed.canExport) return
        val entry = selected() ?: return
        val output = outputTree ?: return
        val hash = analyzedHash ?: return
        job("Saving a verified copy", "Copying and checking the output. Keep the app open; please do not repeat the export.", NextAction.HISTORY, exporting = true) {
            val receipt = store.export(entry, reviewed.company, hash, sourceTree, output) { stage -> mutable.update { it.copy(operation = stage) } }
            val history = runCatching { store.history() }.getOrNull()
            mutable.update { it.copy(savedCopy = receipt, verifiedByUser = false, history = history ?: it.history, historyLoaded = history != null || it.historyLoaded) }
            notify(NoticeKind.SUCCESS, "Copy saved and verified", "${receipt.name}\nOriginal unchanged. Your saved filename and batch folder are shown below.", NextAction.HISTORY)
        }
    }

    fun history() = job("Loading export history", "Reading records stored on this device.", NextAction.HISTORY) {
        val history = store.history()
        mutable.update { it.copy(history = history, historyLoaded = true) }
        if (history.any { !it.completed }) notify(NoticeKind.WARNING, "History includes incomplete exports", "Inspect the output folder before repeating an incomplete export. A partial or unconfirmed file may exist.")
        else notify(NoticeKind.INFO, if (history.isEmpty()) "No exports yet" else "Export history updated", "${history.size} recent records on this device. Completed copies have passed content verification.", announce = false)
    }

    fun scannerResult(uri: Uri) = job("Saving local scan", "Keeping the scanner PDF in app storage before reading it.", NextAction.SCAN) {
        val captures = File(getApplication<Application>().filesDir, "captures").apply { mkdirs() }
        val file = File(captures, "scan-${UUID.randomUUID()}.pdf")
        try {
            resolver.openInputStream(uri).use { input ->
                checkNotNull(input) { "Scanner output could not be opened. Try scanning again." }
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024); var size = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        size += count; require(size <= 200L * 1024 * 1024) { "Scan exceeds the 200 MB pilot limit. Scan fewer pages at a time." }
                        output.write(buffer, 0, count)
                    }
                    require(size > 0) { "Scanner returned an empty file. Try scanning again." }
                }
            }
        } catch (failure: Exception) {
            // Only this newly created, incomplete private capture is removed.
            file.delete()
            throw failure
        }
        val entry = Documents.Entry(Uri.fromFile(file), file.name, "application/pdf", 0)
        entries = listOf(entry); sourceTree = null; analyzedHash = null
        mutable.update { it.copy(sourceLabel = "Camera / gallery scan · app storage", files = listOf(DocumentItem(entry.uri.toString(), entry.name)), selectedId = entry.uri.toString(), analyzed = false, preview = null, company = "", evidence = "", verifiedByUser = false, proposal = "", nameError = null, candidates = emptyList(), savedCopy = null, exportNeedsInspection = false) }
        notify(NoticeKind.SUCCESS, "Scan saved locally", "Read its company name next. The original gallery photo is unchanged. Export important scans before uninstalling the app.")
    }

    private fun preview(entry: Documents.Entry): Bitmap? {
        if (entry.mime == "application/pdf") {
            resolver.openFileDescriptor(entry.uri, "r")?.use { fd ->
                PdfRenderer(fd).use { pdf ->
                    if (pdf.pageCount == 0) return null
                    pdf.openPage(0).use { page ->
                        val scale = 1000f / maxOf(page.width, page.height)
                        return Bitmap.createBitmap(maxOf(1, (page.width * scale).toInt()), maxOf(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888).also { bitmap -> bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                    }
                }
            }
            return null
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(entry.uri).use { BitmapFactory.decodeStream(it, null, options) }
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1200) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        return resolver.openInputStream(entry.uri).use { BitmapFactory.decodeStream(it, null, options) }
    }
}

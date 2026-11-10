package com.local.secrename

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract as DC
import android.provider.OpenableColumns
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

class DemoViewModel(app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(DemoState())
    val state = mutable.asStateFlow()
    private val resolver = app.contentResolver
    private val store = ExportStore(app)
    private val privateCopies = PrivateCopies(app.filesDir)
    private val companyListStore = CompanyListStore(File(app.filesDir, "company-list.dat"))
    private var companyNames: List<String> = emptyList()
    private var pendingWorkbook: CompanyWorkbook? = null
    private var pendingListName = ""
    private var historyCache: List<ExportHistoryItem> = emptyList()
    private val preferences = app.getSharedPreferences("local-workflow", Context.MODE_PRIVATE)
    private val batchProgressStore = BatchProgressStore(preferences)
    private var outputTree: Uri? = null
    private var batchSourceTree: Uri? = null
    private var batchOutputTree: Uri? = null
    private var batchSelection = false
    private val batchJobs = mutableMapOf<String, Job>()
    private var selected: Documents.Entry? = null
    private var sourceParent: Uri? = null
    private var analyzedHash: String? = null
    private var viewerJob: Job? = null
    @Volatile private var discardWhenIdle = false
    private var scannerRequest = 0L
    private var preparingScanner = false
    private var inputRequestId = 0L

    init {
        // Retire the old draft without deleting any imported or captured document files.
        preferences.edit().remove("single.draft").apply()
        job("Restoring local settings", "Checking saved folder access.", NextAction.OUTPUT) {
            try {
                companyListStore.load()?.let { saved ->
                    companyNames = saved.companies
                    mutable.update { it.copy(companyList = CompanyListState(saved.sourceName, saved.companies.size)) }
                }
            } catch (_: Exception) {
                notify(NoticeKind.WARNING, "Company list unavailable", "Import the Excel list again. Document patterns remain available.")
            }
            privateCopies.sweepImports(store.protectedImportSources())
            preferences.getString("output.single", null)?.let { stored ->
                val tree = Uri.parse(stored)
                if (runCatching { store.validateOutputAccess(tree) }.isSuccess) {
                    outputTree = tree
                    mutable.update { it.copy(outputReady = true, outputId = tree.toString(), outputLabel = folderLabel(tree)) }
                } else notify(NoticeKind.WARNING, "Choose the output folder again", "The remembered folder is unavailable or its permission expired.", NextAction.OUTPUT)
            }
            preferences.getString("batch.source", null)?.let { stored ->
                val tree = Uri.parse(stored)
                if (runCatching { Documents(resolver, tree).list() }.isSuccess) {
                    batchSourceTree = tree
                    mutable.update { it.copy(batchSourceLabel = folderLabel(tree)) }
                }
            }
            preferences.getString("batch.output", null)?.let { stored ->
                val tree = Uri.parse(stored)
                if (runCatching { store.validateOutputAccess(tree) }.isSuccess) {
                    batchOutputTree = tree
                    mutable.update { it.copy(batchOutputLabel = folderLabel(tree)) }
                }
            }
            if (batchSourceTree != null && batchOutputTree != null) loadBatchQueue()
        }
    }

    fun enterSingle() {
        if (state.value.busy) return
        batchSelection = false
        outputTree = preferences.getString("output.single", null)?.let(Uri::parse)
        mutable.update { it.copy(screen = AppScreen.SINGLE, outputReady = outputTree != null,
            outputId = outputTree?.toString().orEmpty(), outputLabel = outputTree?.let(::folderLabel) ?: "No output selected") }
    }
    fun enterBatch() { if (!state.value.busy) mutable.update { it.copy(screen = AppScreen.BATCH) } }
    fun batchSource(tree: Uri) = job("Checking source folder", "Listing local documents.", NextAction.SOURCE) {
        require(tree.authority == "com.android.externalstorage.documents") { "Choose a folder on this device." }
        val files = Documents(resolver, tree).list()
        require(files.size <= 10_000) { "This demo supports up to 10,000 source files." }
        batchSourceTree = tree
        check(preferences.edit().putString("batch.source", tree.toString()).commit()) { "Could not remember source folder." }
        mutable.update { it.copy(batchSourceLabel = folderLabel(tree), batchQueue = null, batchStarted = false) }
        if (batchOutputTree != null) loadBatchQueue()
    }
    fun batchOutput(tree: Uri) = job("Checking batch output", "Verifying the destination.", NextAction.OUTPUT) {
        require(tree.authority == "com.android.externalstorage.documents") { "Choose a folder on this device." }
        store.validateOutputAccess(tree)
        batchOutputTree = tree
        check(preferences.edit().putString("batch.output", tree.toString()).commit()) { "Could not remember output folder." }
        mutable.update { it.copy(batchOutputLabel = folderLabel(tree), batchQueue = null, batchStarted = false) }
        if (batchSourceTree != null) loadBatchQueue()
    }
    fun startBatch() {
        if (state.value.busy || batchSourceTree == null || batchOutputTree == null) return
        job("Preparing batch", "Loading the next documents.", NextAction.SOURCE) {
            loadBatchQueue()
            mutable.update { it.copy(batchStarted = true) }
            state.value.batchQueue?.files?.take(10)?.filter { it.status == BatchStatus.PENDING }?.forEach { analyzeBatchFile(it.id) }
        }
    }
    private fun loadBatchQueue() {
        val source = batchSourceTree ?: return
        val output = batchOutputTree ?: return
        val sourceId = DC.getTreeDocumentId(source).trimEnd('/')
        val outputId = DC.getTreeDocumentId(output).trimEnd('/')
        require(sourceId != outputId && !sourceId.startsWith("$outputId/") && !outputId.startsWith("$sourceId/")) {
            "Choose separate source and output folders that do not contain each other."
        }
        val files = Documents(resolver, source).list()
        require(files.size <= 10_000) { "This demo supports up to 10,000 source files." }
        val progress = batchProgressStore.read(source, output)
        val completedIds = progress.completedIds
        val outputChecks = mutableMapOf<String, OutputCheck>()
        files.filter { it.uri.toString() in completedIds }.forEach { entry ->
            val result = store.inspectDuplicate(entry.uri, store.digest(entry.uri), output)
            outputChecks[entry.uri.toString()] = result
        }
        val queue = BatchQueue.create(source.toString(), output.toString(), files.map { entry ->
            BatchFile(entry.uri.toString(), entry.name, entry.mime)
        }).reconcileCompleted(completedIds, progress.skipped, outputChecks)
        val verifiedSaved = queue.files.filter { it.status == BatchStatus.SAVED }.mapTo(mutableSetOf()) { it.id }
        val verifiedSkipped = queue.files.filter { it.status == BatchStatus.ALREADY_PROCESSED }.mapTo(mutableSetOf()) { it.id }
        val returnedToQueue = queue.files.filter { it.id in completedIds && it.status == BatchStatus.PENDING }.mapTo(mutableSetOf()) { it.id }
        if (progress.matches && returnedToQueue.isNotEmpty()) batchProgressStore.reconcile(verifiedSaved, verifiedSkipped)
        mutable.update { it.copy(batchQueue = queue) }
        if (returnedToQueue.isNotEmpty()) notify(NoticeKind.WARNING, "Missing saved copies returned to batch", "${returnedToQueue.size} file(s) no longer exist in the output folder and are available to process again.")
    }
    private fun analyzeBatchFile(id: String) {
        if (batchJobs[id]?.isActive == true) return
        val source = batchSourceTree ?: return
        val entry = Documents(resolver, source).list().firstOrNull { it.uri.toString() == id } ?: return
        val output = batchOutputTree ?: return
        mutable.update { it.copy(batchReviews = it.batchReviews + (id to (it.batchReviews[id] ?: BatchReview()))) }
        batchJobs[id] = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val hash = store.digest(entry.uri)
                    val duplicate = store.inspectDuplicate(entry.uri, hash, output)
                    if (duplicate.match != null) return@withContext Triple("", "", "__ALREADY_PROCESSED__")
                    check(!duplicate.needsInspection) { "Check the output folder and export history before retrying." }
                    val text = LocalOcr.read(resolver, entry) { _, _ -> }
                    check(store.digest(entry.uri) == hash) { "The source document changed during reading." }
                    val match = CompanyMatcher.resolve(text, companyNames)
                    val company = match.suggestedName.orEmpty()
                    val issue = when {
                        text.isBlank() -> "No readable text. Enter the name shown on the document."
                        match.ambiguous -> "Similar company names found. Enter the correct name."
                        company.isBlank() -> "Company name not identified. Enter it manually."
                        else -> null
                    }
                    Triple(text, company, issue)
                }
                if (result.third == "__ALREADY_PROCESSED__") {
                    markBatchAlreadyProcessed(id)
                    return@launch
                }
                mutable.update { current -> current.copy(batchReviews = current.batchReviews + (id to (current.batchReviews[id] ?: BatchReview()).copy(company = result.second, evidence = result.first, issue = result.third, analyzing = false))) }
            } catch (failure: Exception) {
                mutable.update { current -> current.copy(batchReviews = current.batchReviews + (id to (current.batchReviews[id] ?: BatchReview()).copy(issue = friendly(failure), analyzing = false))) }
            }
        }
    }

    private fun markBatchAlreadyProcessed(id: String) {
        val source = batchSourceTree ?: return
        val output = batchOutputTree ?: return
        batchProgressStore.markAlreadyProcessed(source, output, id)
        mutable.update { current ->
            val currentQueue = current.batchQueue
            current.copy(batchReviews = current.batchReviews - id,
            batchQueue = currentQueue?.copy(files = currentQueue.files.map {
                if (it.id == id) it.copy(status = BatchStatus.ALREADY_PROCESSED, issue = null) else it
            }))
        }
        val queue = state.value.batchQueue
        queue?.pending?.take(10)?.forEach { if (it.id !in state.value.batchReviews) analyzeBatchFile(it.id) }
    }

    fun batchCompany(id: String, value: String) {
        if (state.value.batchReviews[id]?.analyzing == true) return
        mutable.update { current -> current.copy(batchReviews = current.batchReviews + (id to (current.batchReviews[id] ?: BatchReview(analyzing = false)).copy(company = value, issue = null))) }
    }

    fun batchPreview(id: String) {
        val source = batchSourceTree ?: return
        val entry = Documents(resolver, source).list().firstOrNull { it.uri.toString() == id } ?: return
        batchSelection = true
        selected = entry
        outputTree = batchOutputTree
        mutable.update { it.copy(files = listOf(DocumentItem(entry.uri.toString(), entry.name, entry.mime)), selectedId = entry.uri.toString()) }
        viewDocument()
    }

    fun saveBatchFile(id: String) {
        val review = state.value.batchReviews[id] ?: return
        if (review.analyzing || review.saving || review.company.isBlank()) return
        val source = batchSourceTree ?: return
        val output = batchOutputTree ?: return
        val entry = Documents(resolver, source).list().firstOrNull { it.uri.toString() == id } ?: return
        val name = review.company
        val filename = runCatching { DocumentRules.allocate(name, entry.extension(), emptyList()) }.getOrElse { failure ->
            mutable.update { current -> current.copy(batchReviews = current.batchReviews + (id to review.copy(issue = failure.message ?: "Enter a valid file name."))) }; return
        }
        mutable.update { it.copy(batchReviews = it.batchReviews + (id to review.copy(saving = true, issue = null))) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val hash = store.digest(entry.uri)
                    val duplicate = store.inspectDuplicate(entry.uri, hash, output)
                    if (duplicate.match != null) return@withContext null
                    check(!duplicate.needsInspection) { "Check the output folder and export history before saving." }
                    store.ensureSeparate(source, output)
                    check(store.digest(entry.uri) == hash) { "The source document changed." }
                    store.export(entry, name, hash, source, output) { }
                }
                if (result == null) {
                    markBatchAlreadyProcessed(id)
                    return@launch
                }
                val receipt = result
                batchProgressStore.markSaved(source, output, id, clearSkipped = true)
                mutable.update { current ->
                    val updatedQueue = current.batchQueue?.markSaved(id, receipt.name)
                    val reviews = current.batchReviews - id
                    current.copy(batchQueue = updatedQueue, batchReviews = reviews)
                }
                state.value.batchQueue?.pending?.take(10)?.forEach { if (it.id !in state.value.batchReviews) analyzeBatchFile(it.id) }
            } catch (failure: Exception) {
                mutable.update { current -> current.copy(batchReviews = current.batchReviews + (id to (current.batchReviews[id] ?: review).copy(saving = false, issue = friendly(failure)))) }
            }
        }
    }

    fun requestBatchSave(id: String) {
        val review = state.value.batchReviews[id] ?: return
        if (!review.analyzing && !review.saving && review.company.isNotBlank()) mutable.update { it.copy(batchConfirmId = id) }
    }

    fun confirmBatchSave() {
        val id = state.value.batchConfirmId ?: return
        mutable.update { it.copy(batchConfirmId = null) }
        saveBatchFile(id)
    }

    fun cancelBatchSave() { mutable.update { it.copy(batchConfirmId = null) } }
    fun home() { if (!state.value.busy) { batchSelection = false; mutable.update { it.copy(screen = AppScreen.HOME, inputRequest = null) } } }
    fun dismissNotice() { mutable.update { it.copy(notice = UserNotice(id = it.notice.id + 1)) } }

    fun importCompanies(uri: Uri) = job("Reading company list", "Reading the local Excel workbook.", NextAction.NONE) {
        require(uri.authority in setOf("com.android.externalstorage.documents", "com.android.providers.media.documents", "com.android.providers.downloads.documents", "media")) {
            "Choose an Excel workbook stored locally on this device."
        }
        val filename = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Company list.xlsx"
        require(filename.endsWith(".xlsx", ignoreCase = true)) { "Choose an .xlsx workbook. Save older .xls files as .xlsx first." }
        val workbook = resolver.openInputStream(uri)?.use(CompanyListReader::read)
            ?: error("The Excel workbook could not be opened.")
        if (discardWhenIdle) return@job
        pendingWorkbook = workbook
        pendingListName = filename
        val sheet = workbook.sheets.indexOfFirst { it.suggestedColumn != null }.coerceAtLeast(0)
        showCompanyColumn(sheet, -1)
    }

    fun selectCompanyColumn(sheetIndex: Int, columnIndex: Int) {
        if (!state.value.busy) showCompanyColumn(sheetIndex, columnIndex)
    }

    private fun showCompanyColumn(sheetIndex: Int, optionIndex: Int) {
        val workbook = pendingWorkbook ?: return
        val sheet = workbook.sheets.getOrNull(sheetIndex) ?: return
        val columns = sheet.columns
        val selectedOption = if (optionIndex >= 0) optionIndex else columns.indexOfFirst { it.index == sheet.suggestedColumn }
        val column = columns.getOrNull(selectedOption)
        val selection = column?.let { runCatching { CompanyListReader.select(sheet, it.index) } }
        val result = selection?.getOrNull()
        val error = when {
            column == null -> "Choose the column containing company names."
            selection?.isFailure == true -> selection.exceptionOrNull()?.message ?: "This column could not be imported."
            result?.names.isNullOrEmpty() -> "No company names found in this column."
            else -> null
        }
        val note = buildList {
            if (sheet.headerRow == null) add("No header detected; the first row is included.")
            if (result != null && result.skippedBlankCount > 0) add("${result.skippedBlankCount} blank rows skipped.")
            if (result != null && result.duplicateCount > 0) add("${result.duplicateCount} duplicate entries skipped.")
        }.joinToString(" ")
        val preview = CompanyListPreview(pendingListName, workbook.sheets.map { it.name }, sheetIndex,
            columns.map { it.label }, selectedOption, result?.names?.take(8).orEmpty(), result?.names?.size ?: 0, note, error)
        mutable.update { it.copy(companyList = it.companyList.copy(preview = preview)) }
    }

    fun cancelCompanyImport() {
        if (state.value.busy) return
        pendingWorkbook = null
        pendingListName = ""
        mutable.update { it.copy(companyList = it.companyList.copy(preview = null)) }
    }

    fun confirmCompanies() {
        val workbook = pendingWorkbook ?: return
        val preview = state.value.companyList.preview ?: return
        if (preview.error != null || preview.count == 0) return
        job("Saving company list", "Storing the validated list on this device.", NextAction.NONE) {
            val sheet = workbook.sheets[preview.sheetIndex]
            val names = CompanyListReader.select(sheet, sheet.columns[preview.columnIndex].index).names
            companyListStore.save(SavedCompanyList(preview.filename, names))
            companyNames = names
            pendingWorkbook = null
            pendingListName = ""
            mutable.update { it.copy(companyList = CompanyListState(preview.filename, names.size)) }
            invalidateCompanyReview()
        }
    }

    fun removeCompanies() = job("Removing company list", "Removing the saved list from this device.", NextAction.NONE) {
        companyListStore.clear()
        companyNames = emptyList()
        pendingWorkbook = null
        mutable.update { it.copy(companyList = CompanyListState()) }
        invalidateCompanyReview()
    }

    private fun invalidateCompanyReview() {
        analyzedHash = null
        mutable.update { it.copy(reviewRevision = it.reviewRevision + 1, analyzed = false, company = "", proposal = "",
            evidence = "", candidates = emptyList(), nameError = null, matchIssue = null) }
    }

    fun requestInput(action: InputAction) {
        if (state.value.busy || state.value.inputRequest != null) return
        val request = InputRequest(++inputRequestId, action)
        mutable.update { it.copy(inputRequest = request) }
        job("Checking save folder", "Verifying the remembered output folder.", NextAction.OUTPUT) {
            val tree = outputTree
            val available = tree != null && runCatching { store.validateOutputAccess(tree) }.isSuccess
            if (!available) {
                outputTree = null
                mutable.update { it.copy(outputReady = false, outputId = "", outputLabel = "No output selected", duplicateChecked = false, reviewRevision = it.reviewRevision + 1) }
            }
            mutable.update { current ->
                current.copy(inputRequest = current.inputRequest?.let { if (it.id == request.id) it.folderChecked(available) else it })
            }
        }
    }

    fun chooseInputFolder() { mutable.update { it.copy(inputRequest = it.inputRequest?.chooseFolder()) } }
    fun cancelInput() { mutable.update { it.copy(inputRequest = null) } }

    fun consumeFolderPicker(id: Long): Boolean {
        val request = state.value.inputRequest ?: return false
        if (state.value.busy || request.id != id || request.step != InputStep.OPEN_FOLDER_PICKER) return false
        mutable.update { it.copy(inputRequest = request.pickerOpened()) }
        return true
    }

    fun consumeInput(id: Long): InputAction? {
        val request = state.value.inputRequest ?: return null
        if (state.value.busy || request.id != id || request.step != InputStep.READY) return null
        cancelInput()
        return request.action
    }

    private fun updateDocument(change: (DemoState) -> DemoState) {
        mutable.update { if (discardWhenIdle) it else change(it) }
    }

    /** Backgrounding clears the UI immediately; an in-flight verified copy may finish safely. */
    fun discardDocument() {
        pendingWorkbook = null
        mutable.update { it.copy(companyList = it.companyList.copy(preview = null)) }
        viewerJob?.cancel()
        if (preparingScanner) {
            preparingScanner = false
            scannerRequest++
            mutable.update { it.copy(busy = false, operation = "", progress = null) }
        }
        discardWhenIdle = state.value.busy
        if (discardWhenIdle) mutable.update { it.clearDocument() } else releaseDocument()
    }

    private fun releaseDocument() {
        val retired = selected
        val reader = viewerJob
        selected = null; sourceParent = null; analyzedHash = null
        mutable.update { it.clearDocument() }
        discardWhenIdle = false
        retireImport(retired, reader)
    }

    private fun retireImport(entry: Documents.Entry?, reader: Job? = viewerJob) {
        if (entry?.uri?.scheme != "file") return
        val file = File(entry.uri.path ?: return)
        viewModelScope.launch(Dispatchers.IO) {
            reader?.join() // A cancelled renderer may still be closing its file descriptor.
            runCatching { privateCopies.retireImport(file, store.protectedImportSources()) }
        }
    }
    fun notify(kind: NoticeKind, title: String, detail: String, action: NextAction = NextAction.NONE, announce: Boolean = true, savedCopy: SavedCopy? = null) {
        val showBanner = NoticePolicy.shouldAnnounce(kind, action, savedCopy)
        if (!announce || (!showBanner && !NoticePolicy.shouldDisplayInline(kind))) return
        mutable.update {
            it.copy(notice = UserNotice(it.notice.id + 1, kind, title, detail, action, showBanner, savedCopy))
        }
    }

    private fun job(title: String, detail: String, recovery: NextAction, exporting: Boolean = false, block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, operation = title, progress = null, notice = UserNotice(id = it.notice.id + 1)) }
        notify(NoticeKind.INFO, title, detail, announce = false)
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                if (exporting) {
                    updateDocument { it.copy(exportNeedsInspection = true) }
                    notify(NoticeKind.ERROR, "Export not confirmed", "${friendly(failure)} Inspect the destination before trying again. Originals are unchanged.", NextAction.HISTORY)
                } else notify(NoticeKind.ERROR, "Could not finish", friendly(failure), recovery)
            } finally {
                if (discardWhenIdle) releaseDocument()
                mutable.update { it.copy(busy = false, operation = "", progress = null) }
            }
        }
    }
    private fun friendly(failure: Exception): String = when (failure) {
        is SecurityException -> "Access is unavailable or the PDF is protected. Choose the folder again or use an approved unlocked document."
        is IOException -> "The document could not be read or written. Check local storage and folder access."
        is IllegalArgumentException, is IllegalStateException -> failure.message?.take(300) ?: "Choose a supported local document and try again."
        else -> "The operation could not be completed. Check the local document and folder access, then try again."
    }
    fun beginScanner(): Long? {
        if (state.value.busy) return null
        preparingScanner = true
        val request = ++scannerRequest
        mutable.update { it.copy(busy = true, operation = "Preparing camera scanner", notice = UserNotice(id = it.notice.id + 1)) }
        notify(NoticeKind.INFO, "Preparing scanner", "First use may prepare resources through Google Play services. Keep the app open.", announce = false)
        return request
    }
    fun scannerPreparationActive(request: Long) = preparingScanner && scannerRequest == request
    fun scannerClosed() { preparingScanner = false; mutable.update { it.copy(busy = false, operation = "", progress = null) } }

    fun output(tree: Uri) = job("Checking output location", "Verifying access and checking the current document for an existing copy.", NextAction.OUTPUT) {
        try {
            store.validateOutputAccess(tree)
            check(preferences.edit().putString("output.single", tree.toString()).commit()) { "The output setting could not be saved. Try again." }
            outputTree = tree
            mutable.update { it.copy(outputLabel = folderLabel(tree), outputReady = true, outputId = tree.toString(), reviewRevision = it.reviewRevision + 1, duplicateChecked = false, duplicate = null, savedCopy = null, exportNeedsInspection = false) }
            val entry = selected
            if (entry == null) notify(NoticeKind.SUCCESS, "Output location remembered", "Single Document will use this location after reopening the app. You can change it at any time.")
            else checkDuplicate(entry, store.digest(entry.uri))
            mutable.update { it.copy(inputRequest = it.inputRequest?.folderAccepted()) }
        } catch (failure: Exception) {
            cancelInput()
            throw failure
        }
    }
    private fun folderLabel(tree: Uri) = DC.getTreeDocumentId(tree).replaceFirst("primary:", "Internal storage / ")

    fun importDocument(uri: Uri) = job("Opening local document", "Making a read-only working copy, then checking for duplicates before OCR.", NextAction.SOURCE) {
        require(uri.authority in setOf("com.android.externalstorage.documents", "com.android.providers.media.documents", "com.android.providers.downloads.documents", "media")) {
            "Choose a photo or file stored locally on this device. Cloud and third-party document providers are not supported in this demo."
        }
        val mime = resolver.getType(uri) ?: error("Cannot identify this file type.")
        require(mime in setOf("application/pdf", "image/jpeg", "image/png")) { "Choose a PDF, JPG, or PNG document." }
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Selected document"
        val file = copyIntoPrivate(uri, mime, "imports")
        val originalParent = if (uri.authority == "com.android.externalstorage.documents" && DC.isDocumentUri(getApplication(), uri)) {
            val id = DC.getDocumentId(uri)
            val parentId = if ('/' in id) id.substringBeforeLast('/') else id.substringBefore(':') + ":"
            DC.buildTreeDocumentUri(uri.authority, parentId)
        } else null
        setSelected(Documents.Entry(Uri.fromFile(file), name, mime, 0), "Local document · original unchanged", originalParent)
    }
    fun scannerResult(uri: Uri) = job("Saving local scan", "Keeping the scanner PDF before reading its company name.", NextAction.SCAN) {
        val file = copyIntoPrivate(uri, "application/pdf", "captures")
        setSelected(Documents.Entry(Uri.fromFile(file), "Camera scan.pdf", "application/pdf", 0), "Camera / scanner gallery · app storage")
    }
    private fun copyIntoPrivate(uri: Uri, mime: String, directory: String): File {
        val folder = File(getApplication<Application>().filesDir, directory).apply { check(mkdirs() || isDirectory) }
        val extension = when (mime) { "application/pdf" -> ".pdf"; "image/png" -> ".png"; else -> ".jpg" }
        val file = File(folder, "${UUID.randomUUID()}$extension")
        try {
            val hash = store.digest(uri)
            resolver.openInputStream(uri).use { input -> file.outputStream().use { out -> CopyIntegrity.copyChecked(checkNotNull(input) { "The selected document could not be opened." }, out, hash) } }
            require(file.length() > 0) { "The selected document is empty." }
            return file
        } catch (failure: Exception) { file.delete(); throw failure }
    }
    private fun setSelected(entry: Documents.Entry, label: String, originalParent: Uri? = null) {
        if (discardWhenIdle) { retireImport(entry); return }
        val previous = selected
        val reader = viewerJob
        viewerJob?.cancel()
        selected = entry; sourceParent = originalParent; analyzedHash = null
        if (previous?.uri != entry.uri) retireImport(previous, reader)
        updateDocument { it.copy(screen = if (batchSelection) AppScreen.BATCH else AppScreen.SINGLE, reviewRevision = it.reviewRevision + 1, sourceLabel = label, files = listOf(DocumentItem(entry.uri.toString(), entry.name, entry.mime)), selectedId = entry.uri.toString(), analyzed = false, company = "", evidence = "", proposal = "", nameError = null, matchIssue = null, candidates = emptyList(), preview = null, viewer = DocumentViewer(), savedCopy = null, duplicate = null, duplicateChecked = false, exportNeedsInspection = false) }
        checkDuplicate(entry, store.digest(entry.uri))
    }
    private fun checkDuplicate(entry: Documents.Entry, hash: String): Boolean {
        if (discardWhenIdle) return false
        val parent = outputTree
        if (parent == null) {
            mutable.update { it.copy(duplicateChecked = false) }
            notify(NoticeKind.INFO, "Choose where to save copies", "Select an output location so the app can check for an existing document before OCR.", NextAction.OUTPUT)
            return false
        }
        mutable.update { it.copy(duplicateChecked = false, operation = "Comparing local file hashes in the output location", progress = null) }
        val result = store.inspectDuplicate(entry.uri, hash, parent)
        if (discardWhenIdle) return false
        val matchingCopy = result.match
        if (result.match == null) store.ensureSeparate(sourceParent, parent)
        updateDocument { it.copy(duplicateChecked = true, duplicate = result.match, exportNeedsInspection = result.needsInspection) }
        when {
            matchingCopy != null -> Unit // The document result already displays the duplicate status.
            result.needsInspection -> Unit // The document result exposes the inspection action.
            else -> notify(NoticeKind.INFO, "Document ready", "No matching saved copy was found. Read the company name, view the document, then approve it for saving.", NextAction.ANALYZE)
        }
        return result.canProceed
    }
    fun name(value: String) {
        if (state.value.busy) return
        val entry = selected ?: return
        val proposal = runCatching { DocumentRules.allocate(value, entry.extension(), emptyList()) }
        updateDocument { it.copy(reviewRevision = it.reviewRevision + 1, company = value, proposal = proposal.getOrDefault(""),
            nameError = proposal.exceptionOrNull()?.message, matchIssue = if (value.isBlank()) it.matchIssue else null) }
    }
    fun analyze() {
        val entry = selected ?: return
        if (!state.value.canAnalyze) return
        job("Reading company name", "Processing locally. Keep the app open until the result is ready.", NextAction.ANALYZE) {
            val hash = store.digest(entry.uri)
            if (!checkDuplicate(entry, hash)) return@job
            analyzedHash = null
            updateDocument { it.copy(reviewRevision = it.reviewRevision + 1, analyzed = false, company = "", proposal = "", nameError = null, matchIssue = null, evidence = "", candidates = emptyList(), savedCopy = null) }
            notify(NoticeKind.INFO, "Reading company name", "Processing the selected document on this device.", announce = false)
            val text = LocalOcr.read(resolver, entry) { done, total ->
                if (discardWhenIdle) throw CancellationException("Document selection cleared")
                mutable.update { it.copy(operation = "$done of $total pages read", progress = if (total > 0) done.toFloat() / total else null) }
            }
            if (discardWhenIdle) return@job
            check(store.digest(entry.uri) == hash) { "The working document changed. Select it again before export." }
            analyzedHash = hash
            val candidates = DocumentRules.candidates(text)
            val match = CompanyMatcher.resolve(text, companyNames)
            val company = match.suggestedName.orEmpty()
            val issue = when {
                text.isBlank() -> "No readable text. View the document or choose a clearer scan."
                match.ambiguous -> "Similar or multiple company names found. Enter the correct name."
                company.isBlank() -> "Company name not identified. Enter the name shown on the document."
                else -> null
            }
            val proposal = runCatching { DocumentRules.allocate(company, entry.extension(), emptyList()) }
            updateDocument { it.copy(analyzed = true, company = company, evidence = text, candidates = candidates, matchIssue = issue,
                proposal = proposal.getOrDefault(""), nameError = if (company.isBlank()) null else proposal.exceptionOrNull()?.message) }
            if (batchSelection) mutable.update { current ->
                val queue = current.batchQueue
                current.copy(batchQueue = queue?.copy(files = queue.files.map { file ->
                    if (file.id == entry.uri.toString()) file.copy(issue = issue) else file
                }))
            }
        }
    }
    fun export(confirmation: SaveConfirmation) {
        if (!state.value.acceptsConfirmation(confirmation)) return
        val entry = selected ?: return
        val parent = outputTree ?: return
        val hash = analyzedHash ?: return
        val reviewedName = confirmation.company
        val originalParent = sourceParent
        job("Saving a verified copy", "Keep the app open while the output is verified.", NextAction.HISTORY, exporting = true) {
            check(store.digest(entry.uri) == hash) { "Document changed. Read and review it again." }
            if (!checkDuplicate(entry, hash)) return@job
            val receipt = store.export(entry, reviewedName, hash, originalParent, parent) { stage -> mutable.update { it.copy(operation = stage) } }
            if (batchSelection) {
                batchProgressStore.markSaved(requireNotNull(batchSourceTree), requireNotNull(batchOutputTree), entry.uri.toString(), clearSkipped = false)
                mutable.update { current -> current.copy(batchQueue = current.batchQueue?.markSaved(entry.uri.toString(), receipt.name)) }
            }
            viewerJob?.cancelAndJoin()
            if (entry.uri.scheme == "file") runCatching {
                privateCopies.retireVerifiedCapture(File(requireNotNull(entry.uri.path)), hash)
            }
            discardDocument()
            batchSelection = false
            notify(NoticeKind.SUCCESS, "File saved", "${receipt.name} saved in ${receipt.folder}", NextAction.CHECK_FILE, savedCopy = receipt)
        }
    }
    fun history() = job("Loading export history", "Reading records on this device.", NextAction.HISTORY) {
        mutable.update { it.copy(historyLoaded = false) }
        historyCache = store.history()
        mutable.update { it.copy(history = historyCache.take(ExportHistory.PAGE_SIZE), historyLoaded = true, historyHasMore = historyCache.size > ExportHistory.PAGE_SIZE) }
    }

    fun moreHistory() {
        if (state.value.busy || !state.value.historyLoaded || !state.value.historyHasMore) return
        mutable.update {
            val count = minOf(it.history.size + ExportHistory.PAGE_SIZE, historyCache.size)
            it.copy(history = historyCache.take(count), historyHasMore = count < historyCache.size)
        }
    }

    fun viewDocument(page: Int = 0, highDetail: Boolean = false) {
        val entry = selected ?: return
        if (state.value.busy || state.value.viewer.busy) return
        mutable.update { it.copy(viewer = DocumentViewer(open = true, busy = true, page = page, pages = it.viewer.pages, highDetail = highDetail)) }
        viewerJob = viewModelScope.launch {
            try {
                val (bitmap, count) = withContext(Dispatchers.IO) { renderPage(entry, page, if (highDetail) 4000 else 2400) }
                mutable.update { it.copy(viewer = DocumentViewer(open = true, page = page, pages = count, image = bitmap.asImageBitmap(), highDetail = highDetail)) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: OutOfMemoryError) { mutable.update { it.copy(viewer = it.viewer.copy(busy = false, error = "Not enough memory for this detail level. Retry at normal detail or use a smaller document.")) } }
            catch (failure: Exception) { mutable.update { it.copy(viewer = it.viewer.copy(busy = false, error = friendly(failure))) } }
        }
    }
    fun closeViewer() { viewerJob?.cancel(); mutable.update { it.copy(viewer = DocumentViewer()) } }
    private fun renderPage(entry: Documents.Entry, index: Int, edge: Int): Pair<Bitmap, Int> {
        if (entry.mime != "application/pdf") return LocalImages.decode(resolver, entry.uri, edge) to 1
        val fd = resolver.openFileDescriptor(entry.uri, "r") ?: error("PDF could not be opened.")
        fd.use { PdfRenderer(it).use { pdf ->
            require(index in 0 until pdf.pageCount) { "The PDF has no readable page at this position." }
            require(pdf.pageCount <= 30) { "This demo supports PDF documents with up to 30 pages." }
            pdf.openPage(index).use { page ->
                val scale = edge.toFloat() / maxOf(page.width, page.height)
                val bitmap = Bitmap.createBitmap(maxOf(1, (page.width * scale).toInt()), maxOf(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
                try { bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                catch (failure: Exception) { bitmap.recycle(); throw failure }
                return bitmap to pdf.pageCount
            }
        } }
    }
}

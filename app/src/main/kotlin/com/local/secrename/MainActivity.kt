package com.local.secrename

import android.content.Intent
import android.content.Context
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.mlkit.vision.documentscanner.*
import com.local.secrename.shared.DemoScreen
import com.local.secrename.shared.NoticeKind
import com.local.secrename.shared.NextAction
import com.local.secrename.shared.SavedCopy
import com.local.secrename.shared.InputAction
import com.local.secrename.shared.InputStep
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[DemoViewModel::class.java] }
    private var externalInputFlow = false
    private class LocalDocumentContract : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent = super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }
    private class LocalFolderContract : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent = super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }
    private val filePicker = registerForActivityResult(LocalDocumentContract()) { uri -> externalInputFlow = false; uri?.let(model::importDocument) }
    private val companyPicker = registerForActivityResult(LocalDocumentContract()) { uri -> externalInputFlow = false; uri?.let(model::importCompanies) }
    private val outputPicker = registerForActivityResult(LocalFolderContract()) { uri ->
        externalInputFlow = false
        if (uri == null) model.cancelInput() else grant(uri)
    }
    private val batchSourcePicker = registerForActivityResult(LocalFolderContract()) { uri ->
        externalInputFlow = false
        uri?.let { batchGrant(it, source = true) }
    }
    private val batchOutputPicker = registerForActivityResult(LocalFolderContract()) { uri ->
        externalInputFlow = false
        uri?.let { batchGrant(it, source = false) }
    }
    // Browsing a saved location never imports a document or changes the selected output.
    private val savedFolderBrowser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val scanner = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        externalInputFlow = false
        model.scannerClosed()
        if (result.resultCode == RESULT_OK) {
            val pdf = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf
            if (pdf != null) model.scannerResult(pdf.uri) else model.notify(NoticeKind.ERROR, "No scan received", "The scanner did not return a PDF. Try again, or import a local PDF or image.", NextAction.SCAN)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalInputFlow = savedInstanceState?.getBoolean("externalInputFlow") ?: false
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect { state ->
                    val request = state.inputRequest
                    if (!state.busy && request != null) when (request.step) {
                        InputStep.OPEN_FOLDER_PICKER -> if (model.consumeFolderPicker(request.id)) launchOutputPicker()
                        InputStep.READY -> when (model.consumeInput(request.id)) {
                            InputAction.FILE -> pickDocument()
                            InputAction.SCAN -> startScan()
                            null -> Unit
                        }
                        else -> Unit
                    }
                }
            }
        }
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            BackHandler(enabled = state.screen != com.local.secrename.shared.AppScreen.HOME && !state.viewer.open) {
                if (state.busy) model.notify(NoticeKind.WARNING, "Operation in progress", "Keep the app open until the current file is finished.") else model.home()
            }
            DemoScreen(state, onSingle = model::enterSingle, onHome = model::home,
                onBatch = model::enterBatch, onBatchSource = { openBatchPicker(true) },
                onBatchOutput = { openBatchPicker(false) }, onStartBatch = model::startBatch,
                onBatchCompany = model::batchCompany, onBatchSave = model::requestBatchSave,
                onBatchConfirm = model::confirmBatchSave, onBatchCancel = model::cancelBatchSave, onBatchPreview = model::batchPreview,
                onFile = { model.requestInput(InputAction.FILE) }, onOutput = ::pickOutput,
                onScan = { model.requestInput(InputAction.SCAN) },
                onChooseInputFolder = model::chooseInputFolder, onCancelInput = model::cancelInput,
                onAnalyze = model::analyze, onName = model::name, onExport = model::export,
                onHistory = model::history, onMoreHistory = model::moreHistory, onDismissNotice = model::dismissNotice,
                onCheckFile = ::checkSavedFile,
                onImportCompanies = ::pickCompanies, onSelectCompanyColumn = model::selectCompanyColumn,
                onConfirmCompanies = model::confirmCompanies, onCancelCompanies = model::cancelCompanyImport, onRemoveCompanies = model::removeCompanies,
                onView = { model.viewDocument() }, onPage = model::viewDocument, onCloseViewer = model::closeViewer)
        }
    }
    override fun onResume() {
        super.onResume()
        externalInputFlow = false
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("externalInputFlow", externalInputFlow)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !externalInputFlow) model.discardDocument()
    }
    private fun checkSavedFile(copy: SavedCopy) {
        if (model.state.value.busy) return
        try {
            val folder = Uri.parse(copy.folderUri)
            require(folder.scheme == "content" && folder.authority == "com.android.externalstorage.documents")
            val available = contentResolver.query(folder, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use {
                it.moveToFirst() && it.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
            } == true
            check(available) { "The saved folder is no longer available." }
            // ACTION_VIEW opens a folder in a compatible file manager. File selection/highlighting
            // is not a portable Android contract, so no vendor-specific extras are required.
            try {
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (_: ActivityNotFoundException) {
                // A dedicated read-only picker is a fallback for devices without a folder viewer.
                savedFolderBrowser.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, folder)
                })
            }
        } catch (_: SecurityException) {
            model.notify(NoticeKind.ERROR, "Folder access unavailable", "Choose the output folder again to restore access.", NextAction.OUTPUT)
        } catch (_: Exception) {
            model.notify(NoticeKind.WARNING, "Could not open folder", "${copy.name} is saved in ${copy.folder}. Open that location in your file manager.")
        }
    }
    private fun pickDocument() {
        if (model.state.value.busy) return
        try { externalInputFlow = true; filePicker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }
        catch (_: Exception) { externalInputFlow = false; model.notify(NoticeKind.ERROR, "File picker unavailable", "The Android Files picker could not open. Try again or use the camera scanner.", NextAction.SOURCE) }
    }
    private fun openBatchPicker(source: Boolean) {
        if (model.state.value.busy) return
        try {
            externalInputFlow = true
            if (source) batchSourcePicker.launch(null) else batchOutputPicker.launch(null)
        } catch (_: Exception) {
            externalInputFlow = false
            model.notify(NoticeKind.ERROR, "Folder picker unavailable", "Try opening the Android Files picker again.")
        }
    }
    private fun batchGrant(uri: Uri, source: Boolean) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                if (source) 0 else Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            if (source) model.batchSource(uri) else model.batchOutput(uri)
        } catch (_: Exception) {
            model.notify(NoticeKind.ERROR, "Folder access unavailable", "Choose a local folder and allow access.")
        }
    }
    private fun pickCompanies() {
        if (model.state.value.busy) return
        try {
            externalInputFlow = true
            companyPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        } catch (_: Exception) {
            externalInputFlow = false
            model.notify(NoticeKind.ERROR, "Excel picker unavailable", "Open a local .xlsx workbook using the Android Files picker.")
        }
    }
    private fun pickOutput() {
        if (model.state.value.busy) return
        model.cancelInput()
        launchOutputPicker()
    }
    private fun launchOutputPicker() {
        try { externalInputFlow = true; outputPicker.launch(null) }
        catch (_: Exception) { externalInputFlow = false; model.cancelInput(); model.notify(NoticeKind.ERROR, "Folder picker unavailable", "Try opening the Android Files picker again.", NextAction.OUTPUT) }
    }
    private fun grant(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            model.output(uri)
        } catch (_: SecurityException) { model.cancelInput(); model.notify(NoticeKind.ERROR, "Folder access was not granted", "Choose a local output folder again and allow access.", NextAction.OUTPUT) }
        catch (_: Exception) { model.cancelInput(); model.notify(NoticeKind.ERROR, "Folder access unavailable", "Choose a local device-storage folder and try again.", NextAction.OUTPUT) }
    }
    private fun startScan() {
        if (model.state.value.busy) return
        val availability = GoogleApiAvailability.getInstance()
        val result = availability.isGooglePlayServicesAvailable(this)
        if (result != ConnectionResult.SUCCESS) {
            model.notify(NoticeKind.WARNING, "Scanner needs Google Play services", "Update or enable Google Play services to scan. Local PDF and image import is still available.", NextAction.SOURCE)
            if (availability.isUserResolvableError(result)) availability.getErrorDialog(this, result, 50)?.show()
            return
        }
        val request = model.beginScanner() ?: return
        val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE).build()
        // Basic mode avoids ML-enabled stain/finger removal that could alter certificate details.
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener {
                if (!model.scannerPreparationActive(request)) return@addOnSuccessListener
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) { model.scannerClosed(); return@addOnSuccessListener }
                try { externalInputFlow = true; scanner.launch(IntentSenderRequest.Builder(it).build()) }
                catch (_: Exception) {
                    externalInputFlow = false
                    model.scannerClosed()
                    model.notify(NoticeKind.ERROR, "Scanner could not open", "Try again, or choose a source folder to import a local scan.", NextAction.SOURCE)
                }
            }
            .addOnFailureListener {
                if (!model.scannerPreparationActive(request)) return@addOnFailureListener
                model.scannerClosed()
                model.notify(NoticeKind.ERROR, "Scanner unavailable", "Check Google Play services and connect for initial scanner setup if needed. You can still import local PDF or image files.", NextAction.SOURCE)
            }
    }
}

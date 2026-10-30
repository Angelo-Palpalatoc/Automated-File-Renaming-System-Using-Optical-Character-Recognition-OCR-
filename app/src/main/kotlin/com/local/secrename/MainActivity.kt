package com.local.secrename

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.mlkit.vision.documentscanner.*
import com.local.secrename.shared.DemoScreen
import com.local.secrename.shared.NoticeKind
import com.local.secrename.shared.NextAction

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[DemoViewModel::class.java] }
    private val sourcePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri == null) model.pickerCancelled(false) else grant(uri, false) }
    private val outputPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri == null) model.pickerCancelled(true) else grant(uri, true) }
    private val scanner = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        model.scannerClosed()
        if (result.resultCode == RESULT_OK) {
            val pdf = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf
            if (pdf != null) model.scannerResult(pdf.uri) else model.notify(NoticeKind.ERROR, "No scan received", "The scanner did not return a PDF. Try again, or import a local PDF or image.", NextAction.SCAN)
        } else model.notify(NoticeKind.INFO, "Scan cancelled", "Your current document is unchanged. Scan again or choose a source folder.", announce = false)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            DemoScreen(state, { pickFolder(false) }, { pickFolder(true) }, ::startScan, model::select, model::analyze, model::name, model::verify, model::export, model::history)
        }
    }
    private fun pickFolder(output: Boolean) {
        try { if (output) outputPicker.launch(null) else sourcePicker.launch(null) }
        catch (_: Exception) { model.notify(NoticeKind.ERROR, "Folder picker unavailable", "The Android Files picker could not be opened. Try again or check the device's file-management apps.", if (output) NextAction.OUTPUT else NextAction.SOURCE) }
    }
    private fun grant(uri: Uri, output: Boolean) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (output) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            contentResolver.takePersistableUriPermission(uri, flags)
            if (output) model.output(uri) else model.source(uri)
        } catch (_: SecurityException) { model.notify(NoticeKind.ERROR, "Folder access was not granted", "Choose a local folder again and allow the requested access. Your current selection is unchanged.", if (output) NextAction.OUTPUT else NextAction.SOURCE) }
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
        if (!model.beginScanner()) return
        val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE).build()
        // Basic mode avoids ML-enabled stain/finger removal that could alter certificate details.
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener {
                try { scanner.launch(IntentSenderRequest.Builder(it).build()) }
                catch (_: Exception) {
                    model.scannerClosed()
                    model.notify(NoticeKind.ERROR, "Scanner could not open", "Try again, or choose a source folder to import a local scan.", NextAction.SOURCE)
                }
            }
            .addOnFailureListener {
                model.scannerClosed()
                model.notify(NoticeKind.ERROR, "Scanner unavailable", "Check Google Play services and connect for initial scanner setup if needed. You can still import local PDF or image files.", NextAction.SOURCE)
            }
    }
}

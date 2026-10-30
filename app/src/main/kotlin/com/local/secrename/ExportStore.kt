package com.local.secrename

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as DC
import android.util.AtomicFile
import com.local.secrename.shared.DocumentRules
import com.local.secrename.shared.SavedCopy
import com.local.secrename.shared.ExportHistoryItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

/** Reads sources; all mutating provider calls are limited to newly created output documents. */
internal class ExportStore(private val context: Context) {
    private val resolver = context.contentResolver
    private val historyDir = File(context.filesDir, "exports").apply { mkdirs() }
    private var batchParent: Uri? = null
    private var batch: Uri? = null

    fun validateLocal(tree: Uri) {
        require(tree.authority == "com.android.externalstorage.documents") { "Choose a local device-storage folder in the system Files picker." }
        require(DC.getTreeDocumentId(tree).startsWith("primary:")) { "The demo supports internal device storage only." }
    }

    fun ensureSeparate(source: Uri?, output: Uri) {
        validateLocal(output)
        if (source == null) return
        validateLocal(source)
        val sourceId = DC.getTreeDocumentId(source).trimEnd('/').lowercase(Locale.ROOT)
        val outputId = DC.getTreeDocumentId(output).trimEnd('/').lowercase(Locale.ROOT)
        val insideSource = if (sourceId.endsWith(':')) outputId.startsWith(sourceId) else outputId.startsWith("$sourceId/")
        require(sourceId != outputId && !insideSource) {
            "Choose output outside the source folder so originals and exports remain separate."
        }
    }

    fun digest(uri: Uri): String = resolver.openInputStream(uri)?.use { CopyIntegrity.sha256(it) } ?: error("Cannot read document.")

    private fun displayName(uri: Uri): String = resolver.query(uri, arrayOf(DC.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
        check(it.moveToFirst()) { "Output file no longer exists." }; it.getString(0)
    } ?: error("Cannot verify output filename.")

    private fun outputBatch(parent: Uri): Uri {
        if (batchParent == parent && batch != null) return batch!!
        val parentDoc = DC.buildDocumentUriUsingTree(parent, DC.getTreeDocumentId(parent))
        val label = "SEC Export " + SimpleDateFormat("yyyy-MM-dd HHmmss", Locale.ROOT).format(Date()) + " " + UUID.randomUUID().toString().take(8)
        val created = DC.createDocument(resolver, parentDoc, DC.Document.MIME_TYPE_DIR, label) ?: error("Cannot create batch output folder.")
        check(displayName(created) == label) { "Provider changed the batch name unexpectedly. Inspect the destination." }
        batchParent = parent; batch = created
        return created
    }

    private fun children(folder: Uri): List<String> {
        val uri = DC.buildChildDocumentsUriUsingTree(folder, DC.getDocumentId(folder))
        return resolver.query(uri, arrayOf(DC.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        } ?: error("Cannot list output folder.")
    }

    fun export(source: Documents.Entry, company: String, expectedHash: String, sourceTree: Uri?, parent: Uri, onStage: (String) -> Unit): SavedCopy {
        onStage("Checking source content and output location")
        ensureSeparate(sourceTree, parent)
        check(digest(source.uri) == expectedHash) { "Source changed after analysis. Analyze it again before export." }
        val folder = outputBatch(parent)
        val target = DocumentRules.allocate(company, source.extension(), children(folder))
        val record = JSONObject().put("source", source.uri.toString()).put("sourceName", source.name)
            .put("targetName", target).put("time", System.currentTimeMillis()).put("expectedSha256", expectedHash).put("status", "pending")
        val log = AtomicFile(File(historyDir, "${UUID.randomUUID()}.json"))
        write(log, record)
        try {
            onStage("Creating a new copy in the batch folder")
            val temporaryName = ".partial-${UUID.randomUUID()}"
            val temporary = DC.createDocument(resolver, folder, source.mime, temporaryName) ?: error("Cannot create output file.")
            record.put("temporaryUri", temporary.toString()); write(log, record)
            resolver.openInputStream(source.uri).use { input ->
                checkNotNull(input) { "Cannot open source." }
                resolver.openOutputStream(temporary, "w").use { output ->
                    checkNotNull(output) { "Cannot write output." }
                    CopyIntegrity.copyChecked(input, output, expectedHash)
                }
            }
            onStage("Verifying copied content")
            check(digest(temporary) == expectedHash) { "Copy verification failed; output is incomplete and must not be used." }
            record.put("status", "verified"); write(log, record)
            check(children(folder).none { it.equals(target, ignoreCase = true) }) { "Target now exists. Retry with a fresh name." }
            onStage("Publishing the filename and confirming the saved copy")
            val published = DC.renameDocument(resolver, temporary, target) ?: error("Provider did not confirm output publication.")
            // Store the returned URI before further checks so uncertain publication is traceable.
            record.put("outputUri", published.toString()).put("status", "published-unconfirmed"); write(log, record)
            check(displayName(published) == target) { "Provider returned an unexpected filename. Inspect export history." }
            check(digest(published) == expectedHash) { "Published copy failed verification." }
            record.put("status", "completed"); write(log, record)
            return SavedCopy(target, displayName(folder))
        } catch (failure: Exception) {
            record.put("status", "incomplete-check-output")
            // Do not log full document contents or exception details containing extracted text.
            runCatching { write(log, record) }
            throw failure
        }
    }

    private fun write(file: AtomicFile, record: JSONObject) {
        val stream = file.startWrite()
        try { stream.write(record.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }

    fun history(): List<ExportHistoryItem> = historyDir.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }?.take(30)?.map {
        runCatching { JSONObject(AtomicFile(it).readFully().toString(Charsets.UTF_8)).let { record ->
            ExportHistoryItem(record.optString("sourceName"), record.optString("targetName"), SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(record.optLong("time", it.lastModified()))), record.optString("status") == "completed")
        } }.getOrDefault(ExportHistoryItem("Unreadable local record", "Inspect the output folder", "Date unavailable", false))
    }.orEmpty()
}

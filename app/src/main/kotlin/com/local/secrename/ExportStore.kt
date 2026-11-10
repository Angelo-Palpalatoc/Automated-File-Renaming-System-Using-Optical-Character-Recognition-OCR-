package com.local.secrename

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as DC
import android.util.AtomicFile
import com.local.secrename.shared.DocumentRules
import com.local.secrename.shared.SavedCopy
import com.local.secrename.shared.ExportHistoryItem
import com.local.secrename.shared.ExportHistory
import com.local.secrename.shared.OutputCheck
import com.local.secrename.shared.DuplicateGuard
import java.io.File
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

/** Reads sources; all mutating provider calls are limited to newly created output documents. */
internal class ExportStore(private val context: Context) {
    private val resolver = context.contentResolver
    private val historyDir = File(context.filesDir, "exports").apply { mkdirs() }

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

    fun validateOutputAccess(tree: Uri) {
        validateLocal(tree)
        val doc = DC.buildDocumentUriUsingTree(tree, DC.getTreeDocumentId(tree))
        displayName(doc)
        check(resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }) {
            "Folder access expired. Choose the output location again."
        }
    }

    /** Checks actual bytes under the selected root, including old/renamed outputs, before OCR. */
    fun inspectDuplicate(source: Uri, hash: String, parent: Uri): OutputCheck {
        validateOutputAccess(parent)
        val rootId = DC.getTreeDocumentId(parent).trimEnd('/')
        fun inRoot(id: String) = id == rootId || if (rootId.endsWith(':')) id.startsWith(rootId) else id.startsWith("$rootId/")
        var completed = false
        var uncertain = false
        // All records participate regardless of the current history page.
        historyDir.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
            val record = runCatching { JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8)) }.getOrElse {
                error("An export record is unreadable. Check export history before saving another copy.")
            }
            if (record.optString("expectedSha256") != hash) return@forEach
            val outputId = record.optString("outputUri").ifBlank { record.optString("temporaryUri") }.takeIf { it.isNotEmpty() }?.let { runCatching { DC.getDocumentId(Uri.parse(it)) }.getOrNull() }
            val scoped = record.optString("outputParent") == parent.toString() || (outputId != null && inRoot(outputId))
            if (scoped) {
                if (record.optString("status") == "completed") completed = true else uncertain = true
            }
        }
        val sourceSize = if (source.scheme == "file") File(checkNotNull(source.path)).length() else -1L
        val outputs = sequence {
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.add(rootId to 0)
            val seen = mutableSetOf<String>()
            var checked = 0
            while (queue.isNotEmpty()) {
                val (folderId, depth) = queue.removeFirst()
                check(depth <= 32) { "Output folders are nested too deeply to verify duplicates. Choose a narrower output location." }
                if (!seen.add(folderId)) continue
                val folder = DC.buildDocumentUriUsingTree(parent, folderId)
                val childrenUri = DC.buildChildDocumentsUriUsingTree(parent, folderId)
                val rows = resolver.query(childrenUri, arrayOf(DC.Document.COLUMN_DOCUMENT_ID, DC.Document.COLUMN_DISPLAY_NAME, DC.Document.COLUMN_MIME_TYPE, DC.Document.COLUMN_SIZE), null, null, null)
                    ?: error("Cannot inspect the output folder. Check folder access before saving.")
                // Close the cursor before yielding: a hash match can stop sequence consumption early.
                val candidates = rows.use { cursor -> buildList<Pair<Uri, String>> {
                    while (cursor.moveToNext()) {
                        check(++checked <= 10000) { "Output contains too many entries to verify safely in this demo. Choose a narrower output location." }
                        val id = cursor.getString(0)
                        check(inRoot(id)) { "Output provider returned an item outside the selected location." }
                        val name = cursor.getString(1)
                        val mime = cursor.getString(2)
                        if (mime == DC.Document.MIME_TYPE_DIR) { queue.add(id to depth + 1); continue }
                        if (name.startsWith(".partial-") || mime !in setOf("application/pdf", "image/jpeg", "image/png")) continue
                        val size = if (cursor.isNull(3)) -1L else cursor.getLong(3)
                        if (size > CopyIntegrity.MAX_BYTES || (size >= 0 && sourceSize >= 0 && size != sourceSize)) continue
                        val uri = DC.buildDocumentUriUsingTree(parent, id)
                        add(uri to name)
                    }
                } }
                val folderName = if (candidates.isEmpty()) "" else folderLabel(folder)
                for ((uri, name) in candidates) yield(digest(uri) to SavedCopy(name, folderName, uri.toString(), folder.toString()))
            }
        }
        return DuplicateGuard.evaluate(hash, outputs, completed, uncertain)
    }

    private fun displayName(uri: Uri): String = resolver.query(uri, arrayOf(DC.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
        check(it.moveToFirst()) { "Output file no longer exists." }; it.getString(0)
    } ?: error("Cannot verify output filename.")

    private fun folderLabel(uri: Uri) = DC.getDocumentId(uri).replaceFirst("primary:", "Internal storage / ")

    private fun children(folder: Uri): List<String> {
        val uri = DC.buildChildDocumentsUriUsingTree(folder, DC.getDocumentId(folder))
        return resolver.query(uri, arrayOf(DC.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        } ?: error("Cannot list output folder.")
    }

    fun export(source: Documents.Entry, company: String, expectedHash: String, sourceTree: Uri?, parent: Uri, onStage: (String) -> Unit): SavedCopy {
        onStage("Checking source content and output location")
        ensureSeparate(sourceTree, parent)
        validateOutputAccess(parent)
        check(digest(source.uri) == expectedHash) { "Source changed after analysis. Analyze it again before export." }
        val folder = DC.buildDocumentUriUsingTree(parent, DC.getTreeDocumentId(parent))
        val target = DocumentRules.allocate(company, source.extension(), children(folder))
        val record = JSONObject().put("source", source.uri.toString()).put("sourceName", source.name)
            .put("targetName", target).put("time", System.currentTimeMillis()).put("expectedSha256", expectedHash).put("status", "pending")
            .put("outputParent", parent.toString())
        val log = AtomicFile(File(historyDir, "${UUID.randomUUID()}.json"))
        write(log, record)
        try {
            onStage("Creating a new copy in the selected output folder")
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
            record.put("status", "completed").put("completedAt", System.currentTimeMillis()); write(log, record)
            return SavedCopy(target, folderLabel(folder), published.toString(), folder.toString())
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

    fun history(): List<ExportHistoryItem> = ExportHistory.ordered(historyDir.listFiles()?.filter { it.extension == "json" }?.map {
        runCatching { JSONObject(AtomicFile(it).readFully().toString(Charsets.UTF_8)).let { record ->
            val timestamp = record.optLong("completedAt", record.optLong("time", it.lastModified()))
            val date = HistoryDates.format(timestamp)
            ExportHistoryItem(record.optString("sourceName"), record.optString("targetName"), date.time, record.optString("status") == "completed", historyCopy(record), it.name, timestamp, date.key, date.label)
        } }.getOrDefault(ExportHistoryItem("Unreadable local record", "Inspect the output folder", "Date unavailable", false, id = it.name))
    }.orEmpty())

    /** An unreadable or incomplete source record prevents cleanup rather than risking its working file. */
    fun protectedImportSources(): Set<File>? = runCatching {
        val records = checkNotNull(historyDir.listFiles()) { "Cannot read export records" }
        check(records.none { it.name.endsWith(".bak") || it.name.endsWith(".new") }) { "An export record is being written or needs inspection" }
        buildSet {
            records.filter { it.extension == "json" }.forEach { file ->
                val record = JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8))
                if (record.optString("status") != "completed") {
                    val source = Uri.parse(record.getString("source"))
                    check(source.scheme == "file" && !source.path.isNullOrBlank())
                    add(File(requireNotNull(source.path)).canonicalFile)
                }
            }
        }
    }.getOrNull()

    /** Use the recorded file's actual parent, including legacy export subfolders. No current setting fallback. */
    private fun historyCopy(record: JSONObject): SavedCopy? = runCatching {
        if (record.optString("status") != "completed") return null
        val output = Uri.parse(record.optString("outputUri").takeIf { it.isNotBlank() } ?: return null)
        require(output.scheme == "content" && output.authority == "com.android.externalstorage.documents" && DC.isTreeUri(output))
        val id = DC.getDocumentId(output)
        val root = DC.getTreeDocumentId(output).trimEnd('/')
        require(id.startsWith("primary:") && root.startsWith("primary:"))
        val parent = id.substringBeforeLast('/', "primary:")
        require(parent == root || if (root.endsWith(':')) parent.startsWith(root) else parent.startsWith("$root/"))
        val folder = DC.buildDocumentUriUsingTree(output, parent)
        SavedCopy(record.optString("targetName"), folderLabel(folder), output.toString(), folder.toString())
    }.getOrNull()
}

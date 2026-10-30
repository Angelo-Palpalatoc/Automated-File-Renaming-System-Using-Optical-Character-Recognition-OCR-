package com.local.secrename

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** Android source-folder adapter. Deliberately exposes no write, rename, or delete operation. */
internal class Documents(private val resolver: ContentResolver, private val tree: Uri) {
    data class Entry(val uri: Uri, val name: String, val mime: String, val flags: Long) {
        fun supported(): Boolean = mime in setOf("application/pdf", "image/jpeg", "image/png")

        fun extension(): String {
            val dot = name.lastIndexOf('.')
            if (dot >= 0) return name.substring(dot)
            return when (mime) {
                "application/pdf" -> ".pdf"
                "image/png" -> ".png"
                else -> ".jpg"
            }
        }
    }

    fun list(): List<Entry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
        return resolver.query(children, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(Entry(
                        uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)),
                        name = cursor.getString(1),
                        mime = cursor.getString(2),
                        flags = cursor.getLong(3),
                    ))
                }
            }
        } ?: error("Cannot list this folder.")
    }
}

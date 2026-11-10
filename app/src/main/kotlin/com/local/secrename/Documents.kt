package com.local.secrename

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** Android source-folder adapter. Deliberately exposes no write, rename, or delete operation. */
internal class Documents(private val resolver: ContentResolver, private val tree: Uri) {
    data class Entry(val uri: Uri, val name: String, val mime: String, val flags: Long) {
        fun supported(): Boolean = mime in setOf("application/pdf", "image/jpeg", "image/png")

        fun extension(): String {
            // Trust the supported MIME type, not an arbitrary provider filename extension.
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
        val entries = resolver.query(children, columns, null, null, null)?.use { cursor ->
            buildList<Entry> {
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
        return entries.filter { it.supported() }.sortedWith { left, right -> naturalCompare(left.name, right.name) }
    }

    private fun naturalCompare(left: String, right: String): Int {
        var a = 0
        var b = 0
        while (a < left.length && b < right.length) {
            if (left[a].isDigit() && right[b].isDigit()) {
                val startA = a
                val startB = b
                while (a < left.length && left[a].isDigit()) a++
                while (b < right.length && right[b].isDigit()) b++
                val numberA = left.substring(startA, a).trimStart('0').ifEmpty { "0" }
                val numberB = right.substring(startB, b).trimStart('0').ifEmpty { "0" }
                val compare = numberA.length.compareTo(numberB.length).takeIf { it != 0 }
                    ?: numberA.compareTo(numberB).takeIf { it != 0 }
                if (compare != null) return compare
            } else {
                val compare = left[a].lowercaseChar().compareTo(right[b].lowercaseChar())
                if (compare != 0) return compare
                a++; b++
            }
        }
        return (left.length - a).compareTo(right.length - b).takeIf { it != 0 }
            ?: left.compareTo(right, ignoreCase = true)
    }
}

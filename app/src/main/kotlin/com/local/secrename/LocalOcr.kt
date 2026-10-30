package com.local.secrename

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.roundToInt

/** Android-only bundled OCR adapter. Call from the existing background worker. */
internal object LocalOcr {
    fun read(resolver: ContentResolver, entry: Documents.Entry, progress: (Int, Int) -> Unit): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            if (entry.mime == "application/pdf") {
                val fd = resolver.openFileDescriptor(entry.uri, "r") ?: error("Cannot open PDF.")
                fd.use {
                    PdfRenderer(fd).use { pdf ->
                        require(pdf.pageCount in 1..30) { "Use a PDF containing 1 to 30 pages. Split larger documents into smaller files." }
                        return buildString {
                            for (index in 0 until pdf.pageCount) {
                                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                                progress(index, pdf.pageCount)
                                pdf.openPage(index).use { page ->
                                    val scale = 2000f / maxOf(page.width, page.height)
                                    val bitmap = Bitmap.createBitmap(
                                        maxOf(1, (page.width * scale).roundToInt()),
                                        maxOf(1, (page.height * scale).roundToInt()),
                                        Bitmap.Config.ARGB_8888,
                                    )
                                    try {
                                        bitmap.eraseColor(Color.WHITE)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        append(Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text)
                                        append("\n\u000c\n")
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                                progress(index + 1, pdf.pageCount)
                            }
                        }
                    }
                }
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(entry.uri).use { BitmapFactory.decodeStream(it, null, options) }
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2400) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            val bitmap = resolver.openInputStream(entry.uri).use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw IllegalArgumentException("Cannot decode image.")
            try {
                progress(0, 1)
                return Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text.also { progress(1, 1) }
            } finally {
                bitmap.recycle()
            }
        } finally {
            recognizer.close()
        }
    }
}

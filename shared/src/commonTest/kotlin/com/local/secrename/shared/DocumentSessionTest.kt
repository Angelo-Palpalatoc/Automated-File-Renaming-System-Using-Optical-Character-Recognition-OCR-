package com.local.secrename.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentSessionTest {
    private val receipt = SavedCopy("COMPANY.pdf", "Output", "content://file", "content://folder")
    private fun selected() = DemoState(
        screen = AppScreen.SINGLE, outputReady = true, outputId = "remembered-output", outputLabel = "Documents / Output",
        files = listOf(DocumentItem("document", "scan.pdf", "application/pdf")), selectedId = "document",
        company = "COMPANY", proposal = "COMPANY.pdf", analyzed = true, duplicateChecked = true,
        evidence = "OCR text", candidates = listOf("COMPANY"), viewer = DocumentViewer(open = true),
    )

    @Test fun clearingRemovesTheDocumentAndReview() {
        val cleared = selected().clearDocument()
        assertNull(cleared.selectedId)
        assertTrue(cleared.files.isEmpty())
        assertEquals("", cleared.company)
        assertEquals("", cleared.evidence)
        assertEquals("", cleared.proposal)
        assertTrue(cleared.candidates.isEmpty())
        assertFalse(cleared.analyzed)
        assertFalse(cleared.viewer.open)
        assertFalse(cleared.canAnalyze)
        assertFalse(cleared.canExport)
    }

    @Test fun destinationAndSavedNotificationSurviveClearing() {
        val notice = UserNotice(kind = NoticeKind.SUCCESS, action = NextAction.CHECK_FILE, savedCopy = receipt)
        val original = selected().copy(notice = notice, historyLoaded = true)
        val cleared = original.clearDocument()
        assertTrue(cleared.outputReady)
        assertEquals(original.outputId, cleared.outputId)
        assertEquals(original.outputLabel, cleared.outputLabel)
        assertEquals(original.screen, cleared.screen)
        assertEquals(receipt, cleared.notice.savedCopy)
        assertTrue(cleared.historyLoaded)
    }

    @Test fun clearingInvalidatesAnAlreadyOpenConfirmation() {
        val original = selected()
        val confirmation = requireNotNull(original.saveConfirmation())
        val cleared = original.clearDocument()
        assertFalse(cleared.acceptsConfirmation(confirmation))
        assertFalse(cleared.copy(files = original.files, selectedId = original.selectedId, analyzed = true, duplicateChecked = true, company = original.company, proposal = original.proposal).acceptsConfirmation(confirmation))
    }

    @Test fun clearingDoesNotPretendAnActiveWriteFinished() {
        val cleared = selected().copy(busy = true, operation = "Verifying copied content", progress = 0.5f).clearDocument()
        assertTrue(cleared.busy)
        assertEquals("Verifying copied content", cleared.operation)
        assertEquals(0.5f, cleared.progress)
        assertFalse(cleared.canExport)
    }

    @Test fun anEmptyNewSessionDoesNotInheritOldDocumentGuards() {
        val cleared = selected().copy(savedCopy = receipt, duplicate = receipt, exportNeedsInspection = true, nameError = "Invalid").clearDocument()
        assertNull(cleared.savedCopy)
        assertNull(cleared.duplicate)
        assertNull(cleared.nameError)
        assertFalse(cleared.duplicateChecked)
        assertFalse(cleared.exportNeedsInspection)
    }
}

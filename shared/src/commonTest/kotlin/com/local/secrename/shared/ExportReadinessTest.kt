package com.local.secrename.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExportReadinessTest {
    private fun reviewed() = DemoState(selectedId = "local-document", analyzed = true, company = "DEMO COMPANY INC.", proposal = "DEMO COMPANY INC.pdf", verifiedByUser = true, outputReady = true)

    @Test fun reviewedDocumentWithSeparateOutputCanBeSaved() { assertTrue(reviewed().canExport) }

    @Test fun invalidFilenameCannotBeSavedEvenAfterReview() {
        val error = runCatching { DocumentRules.allocate("?*:/", ".pdf", emptyList()) }.exceptionOrNull()?.message
        assertNotNull(error)
        assertFalse(reviewed().copy(nameError = error).canExport)
    }

    @Test fun readReviewAndDestinationAreEachRequired() {
        listOf(reviewed().copy(analyzed = false), reviewed().copy(verifiedByUser = false), reviewed().copy(outputReady = false), reviewed().copy(selectedId = null), reviewed().copy(company = " "), reviewed().copy(proposal = "")).forEach {
            assertFalse(it.canExport)
            assertNotNull(it.exportBlocker)
        }
    }

    @Test fun completedCopyCannotBeExportedAgainByRepeatedTap() {
        assertFalse(reviewed().copy(savedCopy = SavedCopy("DEMO COMPANY INC.pdf", "Batch")).canExport)
    }

    @Test fun uncertainPublicationRequiresInspectionBeforeRetry() {
        val state = reviewed().copy(exportNeedsInspection = true)
        assertFalse(state.canExport)
        assertTrue(state.exportBlocker.orEmpty().contains("history"))
    }

    @Test fun anOperationInProgressBlocksAnotherExport() { assertFalse(reviewed().copy(busy = true).canExport) }
}

package com.local.secrename.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExportReadinessTest {
    private fun reviewed() = DemoState(selectedId = "local-document", analyzed = true, company = "DEMO COMPANY INC.", proposal = "DEMO COMPANY INC.pdf", outputId = "local-output", outputReady = true, duplicateChecked = true)

    @Test fun reviewedDocumentWithSeparateOutputCanBeSaved() { assertTrue(reviewed().canExport) }

    @Test fun invalidFilenameCannotBeSavedEvenAfterReview() {
        val error = runCatching { DocumentRules.allocate("?*:/", ".pdf", emptyList()) }.exceptionOrNull()?.message
        assertNotNull(error)
        assertFalse(reviewed().copy(nameError = error).canExport)
    }

    @Test fun readReviewAndDestinationAreEachRequired() {
        listOf(reviewed().copy(analyzed = false), reviewed().copy(outputId = ""), reviewed().copy(outputReady = false), reviewed().copy(selectedId = null), reviewed().copy(company = " "), reviewed().copy(proposal = "")).forEach {
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

    @Test fun uncheckedDestinationCannotBeExported() { assertFalse(reviewed().copy(duplicateChecked = false).canExport) }

    @Test fun existingCopyBlocksOcrAndExportEvenWithChangedCompanyName() {
        val state = reviewed().copy(company = "DIFFERENT NAME INC.", duplicate = SavedCopy("Old name.pdf", "Old batch"))
        assertFalse(state.canAnalyze)
        assertFalse(state.canExport)
    }

    @Test fun missingCompletedCopyCanBeReadAndReviewedNormally() {
        val check = DuplicateGuard.evaluate("same", emptySequence(), completedBefore = true, uncertainBefore = false)
        assertTrue(check.canProceed)
        val imported = reviewed().copy(analyzed = false, duplicateChecked = true, duplicate = check.match, exportNeedsInspection = check.needsInspection)
        assertTrue(imported.canAnalyze)
        assertFalse(imported.canExport)
        assertTrue(imported.copy(analyzed = true).canExport)
        assertNotNull(imported.copy(analyzed = true).saveConfirmation())
    }

    @Test fun confirmationMustMatchDocumentNameFilenameAndDestination() {
        val state = reviewed()
        val confirmation = assertNotNull(state.saveConfirmation())
        assertTrue(state.acceptsConfirmation(confirmation))
        listOf(
            state.copy(selectedId = "another-document"),
            state.copy(company = "ANOTHER COMPANY INC."),
            state.copy(proposal = "ANOTHER FILE.pdf"),
            state.copy(outputId = "another-output"),
            // Even changing a name away and back requires a new review.
            state.copy(reviewRevision = state.reviewRevision + 1),
        ).forEach { assertFalse(it.acceptsConfirmation(confirmation)) }
    }

    @Test fun confirmationCannotBypassBusyDuplicateOrInspectionGuards() {
        val state = reviewed()
        val confirmation = assertNotNull(state.saveConfirmation())
        listOf(
            state.copy(busy = true), state.copy(duplicateChecked = false),
            state.copy(duplicate = SavedCopy("Existing.pdf", "Export")),
            state.copy(savedCopy = SavedCopy("Saved.pdf", "Export")),
            state.copy(exportNeedsInspection = true),
            state.copy(nameError = "Invalid name"), state.copy(analyzed = false),
        ).forEach { assertFalse(it.acceptsConfirmation(confirmation)) }
    }

    @Test fun progressAndNotificationsDoNotInvalidateReviewedContent() {
        val state = reviewed()
        val confirmation = assertNotNull(state.saveConfirmation())
        assertTrue(state.copy(notice = UserNotice(id = 1), operation = "Complete").acceptsConfirmation(confirmation))
    }
}

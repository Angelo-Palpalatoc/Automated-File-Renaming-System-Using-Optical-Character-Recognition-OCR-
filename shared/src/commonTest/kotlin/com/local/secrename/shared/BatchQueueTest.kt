package com.local.secrename.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatchQueueTest {
    @Test fun moreThanOneHundredFilesSortNaturallyAndKeepUnresolvedPending() {
        val files = (1..123).reversed().map { number ->
            BatchFile("source-$number", "file$number.pdf", "application/pdf")
        }
        val queue = BatchQueue.create("source", "output", files)
        assertEquals("file1.pdf", queue.files.first().name)
        assertEquals("file10.pdf", queue.files[9].name)
        assertEquals("file123.pdf", queue.files.last().name)
        assertEquals(10, queue.visible.size)
        assertTrue(queue.hasMore)

        val unresolved = queue.copy(files = queue.files.map { if (it.name == "file10.pdf") it.copy(issue = "Needs review") else it })
        val savedNine = unresolved.files.take(9).fold(unresolved) { current, file -> current.markSaved(file.id, file.name) }
        assertEquals(9, savedNine.savedCount)
        assertEquals("file10.pdf", savedNine.pending.first().name)
        assertEquals("Needs review", savedNine.pending.first().issue)
        assertFalse(savedNine.files[9].status == BatchStatus.SAVED)
    }

    @Test fun sameContentUnderAnotherSourceNameIsFinishedWithoutAnotherSave() {
        val queue = BatchQueue.create("source", "output", listOf(
            BatchFile("renamed-source-uri", "renamed-copy.pdf", "application/pdf"),
            BatchFile("new-uri", "another-name.pdf", "application/pdf"),
        ))
        val copy = SavedCopy("client-copy.pdf", "Output")
        val reconciled = queue.reconcileCompleted(
            completedIds = setOf("new-uri"), alreadyProcessedIds = setOf("new-uri"),
            checks = mapOf("new-uri" to OutputCheck(match = copy)),
        )
        assertEquals(BatchStatus.ALREADY_PROCESSED, reconciled.files.single { it.id == "new-uri" }.status)
        assertFalse(reconciled.pending.any { it.id == "new-uri" })
    }

    @Test fun deletedCompletedOutputReturnsSourceToPendingQueue() {
        val queue = BatchQueue.create("source", "output", listOf(BatchFile("source-uri", "scan.pdf", "application/pdf")))
        val reconciled = queue.reconcileCompleted(
            completedIds = setOf("source-uri"), alreadyProcessedIds = emptySet(),
            checks = mapOf("source-uri" to OutputCheck(missingPreviousCopy = true)),
        )
        assertEquals(BatchStatus.PENDING, reconciled.files.single().status)
        assertEquals("source-uri", reconciled.pending.single().id)
    }

    @Test fun uncertainPriorExportStaysProtectedFromAutomaticRetry() {
        val queue = BatchQueue.create("source", "output", listOf(BatchFile("source-uri", "scan.pdf", "application/pdf")))
        val reconciled = queue.reconcileCompleted(
            completedIds = setOf("source-uri"), alreadyProcessedIds = emptySet(),
            checks = mapOf("source-uri" to OutputCheck(needsInspection = true)),
        )
        assertEquals(BatchStatus.SAVED, reconciled.files.single().status)
        assertTrue(reconciled.pending.isEmpty())
    }
}

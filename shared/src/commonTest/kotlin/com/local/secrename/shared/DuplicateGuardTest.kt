package com.local.secrename.shared

import kotlin.test.*

class DuplicateGuardTest {
    private val copy = SavedCopy("COMPANY (7).pdf", "Previous export")
    @Test fun renamedFileWithMatchingBytesIsStillDuplicate() {
        assertEquals(copy, DuplicateGuard.evaluate("same", sequenceOf("different" to copy.copy(name = "Other.pdf"), "same" to copy), false, false).match)
    }
    @Test fun sameCompanyButDifferentContentCanBeExported() {
        assertNull(DuplicateGuard.evaluate("new-content", sequenceOf("old-content" to copy), false, false).match)
    }
    @Test fun completedRecordDoesNotBlockWhenSavedBytesAreMissing() {
        val result = DuplicateGuard.evaluate("same", emptySequence(), true, false)
        assertNull(result.match)
        assertTrue(result.missingPreviousCopy)
        assertTrue(result.canProceed)
    }
    @Test fun uncertainPublicationRequiresInspection() {
        val result = DuplicateGuard.evaluate("same", emptySequence(), true, true)
        assertTrue(result.needsInspection)
        assertFalse(result.missingPreviousCopy)
        assertFalse(result.canProceed)
    }
    @Test fun verifiedExistingContentPreventsAnotherCopyDespiteUncertainJournal() {
        assertEquals(copy, DuplicateGuard.evaluate("same", sequenceOf("same" to copy), false, true).match)
    }
    @Test fun checkStopsReadingAsSoonAsMatchIsVerified() {
        val outputs = sequence { yield("same" to copy); error("Unnecessary file read") }
        assertEquals(copy, DuplicateGuard.evaluate("same", outputs, false, false).match)
    }
    @Test fun aCopyReappearingBeforeSaveBlocksReplacement() {
        assertTrue(DuplicateGuard.evaluate("same", emptySequence(), true, false).canProceed)
        val recheck = DuplicateGuard.evaluate("same", sequenceOf("same" to copy), true, false)
        assertEquals(copy, recheck.match)
        assertFalse(recheck.canProceed)
    }
    @Test fun anUnreadableDestinationCannotBeTreatedAsAMissingCopy() {
        val unreadable = sequence<Pair<String, SavedCopy>> { error("Cannot inspect output folder") }
        assertFailsWith<IllegalStateException> { DuplicateGuard.evaluate("same", unreadable, true, false) }
    }
}

package com.local.secrename.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportHistoryTest {
    private val copy = SavedCopy("COMPANY.pdf", "Old output / Export", "content://old/file", "content://old/folder")
    private val item = ExportHistoryItem("scan.pdf", copy.name, "Nov 3, 2026", true, copy)

    @Test fun historyOpensItsRecordedDestinationAfterOutputChanges() {
        val state = DemoState(outputId = "content://new/folder", history = listOf(item))
        assertEquals(copy, state.history.single().checkFileCopy)
        assertEquals(copy, state.clearDocument().history.single().checkFileCopy)
    }

    @Test fun incompleteExportDoesNotOfferAVerifiedFileLink() {
        assertNull(item.copy(completed = false).checkFileCopy)
    }

    @Test fun legacyRecordWithoutDestinationStillAppearsWithoutAnOpenAction() {
        assertNull(item.copy(savedCopy = null).checkFileCopy)
        assertEquals("COMPANY.pdf", item.copy(savedCopy = null).target)
    }

    @Test fun unavailableDestinationIsNeverReplacedWithCurrentOutput() {
        assertNull(item.copy(savedCopy = copy.copy(folderUri = "")).checkFileCopy)
    }

    @Test fun groupsHaveOneDividerPerLocalDayWithNewestExportsFirst() {
        val today = item.copy(id = "today", exportedAt = 30, dayKey = "2026-11-03", dayLabel = "November 3, 2026")
        val earlier = today.copy(id = "earlier", exportedAt = 20)
        val yesterday = item.copy(id = "yesterday", exportedAt = 10, dayKey = "2026-11-02", dayLabel = "November 2, 2026")
        val days = ExportHistory.days(listOf(yesterday, earlier, today))
        assertEquals(listOf("November 3, 2026", "November 2, 2026"), days.map { it.label })
        assertEquals(listOf("today", "earlier"), days.first().items.map { it.id })
    }

    @Test fun permanentHistoryIsNotLimitedToThirtyEntries() {
        val records = (1..125).map { item.copy(id = "$it", exportedAt = it.toLong()) }
        val ordered = ExportHistory.ordered(records)
        assertEquals(125, ordered.size)
        assertEquals("125", ordered.first().id)
        assertEquals("1", ordered.last().id)
        assertTrue(ordered.size > ExportHistory.PAGE_SIZE)
    }

    @Test fun appendingAnotherPageDoesNotRepeatTheSameDayDivider() {
        val records = (1..60).map { item.copy(id = "$it", dayKey = "2026-11-03", dayLabel = "November 3, 2026") }
        assertEquals(1, ExportHistory.days(records).size)
        assertEquals(60, ExportHistory.days(records).single().items.size)
    }
}

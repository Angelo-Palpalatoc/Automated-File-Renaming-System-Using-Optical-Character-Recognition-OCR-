package com.local.secrename.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoticePolicyTest {
    private val copy = SavedCopy("COMPANY.pdf", "Output", "content://local/document/company", "content://local/document/output")

    @Test fun routineInformationDoesNotRaiseAlerts() {
        NextAction.entries.forEach { action -> assertFalse(NoticePolicy.shouldAnnounce(NoticeKind.INFO, action, copy)) }
    }

    @Test fun normalSuccessesRemainQuiet() {
        assertFalse(NoticePolicy.shouldAnnounce(NoticeKind.SUCCESS, NextAction.NONE, null))
        assertFalse(NoticePolicy.shouldAnnounce(NoticeKind.SUCCESS, NextAction.REVIEW, null))
        assertFalse(NoticePolicy.shouldAnnounce(NoticeKind.SUCCESS, NextAction.HISTORY, copy))
    }

    @Test fun completedSaveIsActionableAndRequiresItsOwnDestination() {
        assertTrue(NoticePolicy.shouldAnnounce(NoticeKind.SUCCESS, NextAction.CHECK_FILE, copy))
        assertFalse(NoticePolicy.shouldAnnounce(NoticeKind.SUCCESS, NextAction.CHECK_FILE, null))
        assertFalse(NoticePolicy.shouldAnnounce(NoticeKind.SUCCESS, NextAction.CHECK_FILE, copy.copy(folderUri = "")))
    }

    @Test fun issuesRemainInlineWithoutRaisingPopups() {
        for (kind in listOf(NoticeKind.WARNING, NoticeKind.ERROR)) {
            NextAction.entries.forEach { action -> assertFalse(NoticePolicy.shouldAnnounce(kind, action, null)) }
            assertTrue(NoticePolicy.shouldDisplayInline(kind))
        }
        assertFalse(NoticePolicy.shouldDisplayInline(NoticeKind.INFO))
        assertFalse(NoticePolicy.shouldDisplayInline(NoticeKind.SUCCESS))
    }

    @Test fun anOlderNoticeRetainsItsOwnFileAndFolder() {
        val notice = UserNotice(kind = NoticeKind.SUCCESS, action = NextAction.CHECK_FILE, savedCopy = copy)
        val state = DemoState(notice = notice, savedCopy = copy.copy(name = "OTHER.pdf", folderUri = "content://local/document/other-output"))
        assertEquals(copy, state.notice.savedCopy)
    }
}

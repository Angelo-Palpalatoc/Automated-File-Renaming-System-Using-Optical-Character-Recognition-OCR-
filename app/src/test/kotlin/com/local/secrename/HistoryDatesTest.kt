package com.local.secrename

import org.junit.Test
import org.junit.Assert.assertEquals
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class HistoryDatesTest {
    @Test fun groupUsesTheDevicesLocalExportDateAcrossUtcMidnight() {
        val timestamp = Instant.parse("2026-11-02T17:30:00Z").toEpochMilli()
        val group = HistoryDates.format(timestamp, TimeZone.getTimeZone("Asia/Shanghai"), Locale.US)
        assertEquals("2026-11-03", group.key)
        assertEquals("November 3, 2026", group.label)
        assertEquals("1:30 AM", group.time)
    }

    @Test fun previousLocalDayHasASeparateDivider() {
        val timestamp = Instant.parse("2026-11-02T15:59:00Z").toEpochMilli()
        assertEquals("2026-11-02", HistoryDates.format(timestamp, TimeZone.getTimeZone("Asia/Shanghai"), Locale.US).key)
    }

    @Test fun regroupingAfterATimezoneChangeUsesThatTimezone() {
        val timestamp = Instant.parse("2026-11-03T00:30:00Z").toEpochMilli()
        assertEquals("2026-11-02", HistoryDates.format(timestamp, TimeZone.getTimeZone("America/Los_Angeles"), Locale.US).key)
    }
}

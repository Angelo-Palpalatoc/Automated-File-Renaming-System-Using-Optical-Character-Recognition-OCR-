package com.local.secrename

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class HistoryDate(val key: String, val label: String, val time: String)

internal object HistoryDates {
    fun format(timestamp: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): HistoryDate {
        fun label(pattern: String, formatLocale: Locale = locale) = SimpleDateFormat(pattern, formatLocale).apply { timeZone = zone }.format(Date(timestamp))
        return HistoryDate(label("yyyy-MM-dd", Locale.ROOT), label("MMMM d, yyyy"), label("h:mm a"))
    }
}

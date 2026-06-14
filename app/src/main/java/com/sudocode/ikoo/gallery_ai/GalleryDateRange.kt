package com.sudocode.ikoo.gallery_ai

import java.util.Calendar
import java.util.Date

/**
 * Half-open date range [start, end). Ported from the Flutter implementation.
 */
data class GalleryDateRange(val start: Date, val end: Date) {
    fun contains(date: Date): Boolean {
        return !date.before(start) && date.before(end)
    }

    companion object {
        fun day(day: Date): GalleryDateRange {
            val cal = Calendar.getInstance()
            cal.time = day
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.time
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val end = cal.time
            return GalleryDateRange(start, end)
        }
    }
}

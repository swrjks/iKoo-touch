package com.sudocode.ikoo.calendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.sudocode.ikoo.history.HistoryRepository
import com.sudocode.ikoo.intent.EventData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object CalendarActionManager {
    fun openCalendarInsert(context: Context, eventData: EventData) {
        val startMillis = parseStartMillis(eventData)
        val title = eventData.cleanCalendarTitle()
        val intent = Intent(Intent.ACTION_INSERT)
            .setDataAndType(CalendarContract.Events.CONTENT_URI, "vnd.android.cursor.item/event")
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, "Added via iKoo")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        eventData.location?.takeIf { it.isNotBlank() }?.let { location ->
            intent.putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        }

        startMillis?.let { beginsAt ->
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginsAt)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginsAt + DEFAULT_EVENT_DURATION_MILLIS)
        }

        HistoryRepository.getInstance(context).markLatestActionTaken("Calendar insert opened")
        context.startActivity(intent)
    }

    private fun parseStartMillis(eventData: EventData): Long? {
        val time = eventData.timePhrase?.let(::parseTimePhrase) ?: return null
        val date = eventData.datePhrase?.let(::parseDatePhrase)
            ?: defaultDateFor(time)
            ?: return null
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun EventData.cleanCalendarTitle(): String {
        val cleaned = title
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\b(show contact information|add emoji reaction|smart reply|tap to edit|reply|more options|to me)\\b.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(search in mail|open navigation drawer|signed in as|google one|inbox)\\b.*", RegexOption.IGNORE_CASE), "")
            .trim(' ', '-', ':', '|', ',', '.')
            .take(64)
            .trim()
        return cleaned.ifBlank { "Event" }
    }

    private fun defaultDateFor(time: LocalTime): LocalDate {
        val today = LocalDate.now(ZoneId.systemDefault())
        val now = LocalTime.now(ZoneId.systemDefault())
        return if (time.isBefore(now.minusMinutes(5))) today.plusDays(1) else today
    }

    private fun parseDatePhrase(datePhrase: String): LocalDate? {
        val today = LocalDate.now(ZoneId.systemDefault())
        return when (datePhrase.normalized()) {
            "today", "tonight" -> today
            "tomorrow" -> today.plusDays(1)
            "monday", "mon" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
            "tuesday", "tue" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY))
            "wednesday", "wed" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
            "thursday", "thu" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY))
            "friday", "fri" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
            "saturday", "sat" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            "sunday", "sun" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            else -> null
        }
    }

    private fun parseTimePhrase(timePhrase: String): LocalTime? {
        val normalized = timePhrase.normalized()
        if (normalized == "noon") return LocalTime.NOON
        if (normalized == "midnight") return LocalTime.MIDNIGHT

        val match = TIME_PATTERN.matchEntire(normalized) ?: return null
        val hourText = match.groupValues[1]
        val minuteText = match.groupValues[3]
        val meridiem = match.groupValues[4]
        var hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0

        if (hour !in 0..23 || minute !in 0..59) return null
        hour = when {
            meridiem == "am" && hour == 12 -> 0
            meridiem == "pm" && hour != 12 -> hour + 12
            else -> hour
        }

        return LocalTime.of(hour, minute)
    }

    private fun String.normalized(): String {
        return trim()
            .lowercase(Locale.US)
            .replace(".", ":")
            .replace(Regex("\\s+(\\d{2})\\b"), ":$1")
            .replace(Regex("\\s+"), " ")
    }

    private const val DEFAULT_EVENT_DURATION_MILLIS = 60L * 60L * 1_000L
    private val TIME_PATTERN = Regex("(\\d{1,2})(:(\\d{2}))?\\s*(am|pm)?")
}

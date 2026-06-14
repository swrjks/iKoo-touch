package com.sudocode.ikoo.intent

enum class IntentType {
    CALENDAR_EVENT,
    REMINDER,
    TASK,
    NONE
}

data class IntentDetectionResult(
    val type: IntentType,
    val confidence: Float,
    val matchedText: String,
    val reason: String
)

object IntentDetector {
    fun detect(visibleText: String): IntentDetectionResult {
        val normalizedText = visibleText.normalizeWhitespace()
        if (normalizedText.isBlank()) {
            return IntentDetectionResult(
                type = IntentType.NONE,
                confidence = 0f,
                matchedText = "",
                reason = "No visible text available."
            )
        }

        return normalizedText
            .candidatePhrases()
            .map(::scorePhrase)
            .maxWithOrNull(compareBy<IntentDetectionResult> { it.confidence }.thenBy { it.type.ordinal })
            ?.takeIf { it.confidence >= MIN_CONFIDENCE }
            ?: IntentDetectionResult(
                type = IntentType.NONE,
                confidence = 0.12f,
                matchedText = normalizedText.take(MAX_MATCHED_TEXT_LENGTH),
                reason = "No strong calendar, reminder, or task language was found."
            )
    }

    private fun scorePhrase(phrase: String): IntentDetectionResult {
        val text = phrase.normalizeWhitespace()
        val hasTime = TIME_PATTERN.containsMatchIn(text)
        val hasDate = DATE_PATTERN.containsMatchIn(text)
        val hasCalendarNoun = CALENDAR_NOUN_PATTERN.containsMatchIn(text)
        val hasReminderVerb = REMINDER_VERB_PATTERN.containsMatchIn(text)
        val hasTaskVerb = TASK_VERB_PATTERN.containsMatchIn(text)
        val hasDeadline = DEADLINE_PATTERN.containsMatchIn(text)
        val hasLocation = LOCATION_PATTERN.containsMatchIn(text)

        val calendarScore = weightedScore(
            0.22f,
            0.30f to hasDate,
            0.28f to hasTime,
            0.16f to hasCalendarNoun,
            0.08f to hasLocation
        )
        val reminderScore = weightedScore(
            0.18f,
            0.34f to hasReminderVerb,
            0.22f to hasDate,
            0.10f to hasTime,
            0.08f to !hasTaskVerb
        )
        val taskScore = weightedScore(
            0.18f,
            0.36f to hasTaskVerb,
            0.22f to hasDeadline,
            0.12f to hasTime,
            0.06f to !hasCalendarNoun
        )

        return when {
            calendarScore >= reminderScore && calendarScore >= taskScore -> {
                IntentDetectionResult(
                    type = IntentType.CALENDAR_EVENT,
                    confidence = calendarScore.coerceAtMost(0.96f),
                    matchedText = text.take(MAX_MATCHED_TEXT_LENGTH),
                    reason = buildReason(
                        "Calendar event",
                        "date or day" to hasDate,
                        "time" to hasTime,
                        "event wording" to hasCalendarNoun,
                        "location" to hasLocation
                    )
                )
            }
            taskScore >= reminderScore -> {
                IntentDetectionResult(
                    type = IntentType.TASK,
                    confidence = taskScore.coerceAtMost(0.94f),
                    matchedText = text.take(MAX_MATCHED_TEXT_LENGTH),
                    reason = buildReason(
                        "Task",
                        "action verb" to hasTaskVerb,
                        "deadline wording" to hasDeadline,
                        "time" to hasTime
                    )
                )
            }
            else -> {
                IntentDetectionResult(
                    type = IntentType.REMINDER,
                    confidence = reminderScore.coerceAtMost(0.92f),
                    matchedText = text.take(MAX_MATCHED_TEXT_LENGTH),
                    reason = buildReason(
                        "Reminder",
                        "reminder action" to hasReminderVerb,
                        "date or day" to hasDate,
                        "time" to hasTime
                    )
                )
            }
        }
    }

    private fun weightedScore(base: Float, vararg features: Pair<Float, Boolean>): Float {
        return features.fold(base) { score, feature ->
            if (feature.second) score + feature.first else score
        }
    }

    private fun buildReason(label: String, vararg signals: Pair<String, Boolean>): String {
        val matchedSignals = signals
            .filter { it.second }
            .joinToString { it.first }
        return if (matchedSignals.isBlank()) {
            "$label language was the closest local match."
        } else {
            "$label detected from $matchedSignals."
        }
    }

    private fun String.candidatePhrases(): List<String> {
        val phrases = SENTENCE_BOUNDARY_PATTERN
            .split(this)
            .map { it.normalizeWhitespace() }
            .filter { it.isNotBlank() }
        return phrases.ifEmpty { listOf(this) }
    }

    private fun String.normalizeWhitespace(): String {
        return trim().replace(WHITESPACE_PATTERN, " ")
    }

    private const val MIN_CONFIDENCE = 0.55f
    private const val MAX_MATCHED_TEXT_LENGTH = 160

    private val WHITESPACE_PATTERN = Regex("\\s+")
    private val SENTENCE_BOUNDARY_PATTERN = Regex("[\\n.!?;]+")
    private val TIME_PATTERN = Regex(
        "\\b(\\d{1,2}([:.]\\d{2}|\\s+\\d{2})\\s?(am|pm)?|\\d{1,2}\\s?(am|pm)|noon|midnight)\\b",
        RegexOption.IGNORE_CASE
    )
    private val DATE_PATTERN = Regex(
        "\\b(today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
            "mon|tue|wed|thu|fri|sat|sun|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|" +
            "\\d{1,2}/\\d{1,2}(/\\d{2,4})?|\\d{1,2}-\\d{1,2}(-\\d{2,4})?)\\b",
        RegexOption.IGNORE_CASE
    )
    private val CALENDAR_NOUN_PATTERN = Regex(
        "\\b(meeting|dinner|lunch|breakfast|appointment|call|interview|reservation|party|class|session|visit|exam|test)\\b",
        RegexOption.IGNORE_CASE
    )
    private val REMINDER_VERB_PATTERN = Regex(
        "\\b(call|remind|remember|text|message|ping|email|wake|follow up|check in)\\b",
        RegexOption.IGNORE_CASE
    )
    private val TASK_VERB_PATTERN = Regex(
        "\\b(submit|finish|complete|send|pay|file|review|prepare|update|upload|deliver|draft|sign)\\b",
        RegexOption.IGNORE_CASE
    )
    private val DEADLINE_PATTERN = Regex(
        "\\b(before|by|due|deadline|until|no later than|eod|end of day)\\b",
        RegexOption.IGNORE_CASE
    )
    private val LOCATION_PATTERN = Regex("\\b(at|in|near)\\s+[A-Z][\\p{L}\\p{N}'-]+", RegexOption.IGNORE_CASE)
}

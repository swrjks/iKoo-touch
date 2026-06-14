package com.sudocode.ikoo.intent

data class EventData(
    val title: String,
    val datePhrase: String?,
    val timePhrase: String?,
    val location: String?,
    val confidence: Float
)

object EventExtractor {
    fun extract(calendarText: String): EventData? {
        val normalizedText = calendarText.normalizeWhitespace()
        if (normalizedText.isBlank()) return null

        val datePhrase = DATE_PATTERN.find(normalizedText)?.value?.cleanPhrase()
        val timePhrase = TIME_PATTERN.find(normalizedText)?.value?.cleanPhrase()
        val location = LOCATION_PATTERN.find(normalizedText)
            ?.groupValues
            ?.getOrNull(1)
            ?.cleanPhrase()

        val title = normalizedText
            .removeMatched(datePhrase)
            .removeMatched(timePhrase)
            .removeLocationPhrase(location)
            .removeTrailingConnectors()
            .cleanPhrase()
            .ifBlank { inferTitle(normalizedText) }

        val confidence = buildConfidence(
            hasTitle = title.isNotBlank(),
            hasDate = datePhrase != null,
            hasTime = timePhrase != null,
            hasLocation = location != null
        )

        return EventData(
            title = title,
            datePhrase = datePhrase,
            timePhrase = timePhrase,
            location = location,
            confidence = confidence
        )
    }

    private fun buildConfidence(
        hasTitle: Boolean,
        hasDate: Boolean,
        hasTime: Boolean,
        hasLocation: Boolean
    ): Float {
        var score = 0.24f
        if (hasTitle) score += 0.20f
        if (hasDate) score += 0.24f
        if (hasTime) score += 0.22f
        if (hasLocation) score += 0.10f
        return score.coerceAtMost(0.98f)
    }

    private fun inferTitle(text: String): String {
        return text
            .split(" ")
            .take(3)
            .joinToString(" ")
            .cleanPhrase()
    }

    private fun String.removeMatched(phrase: String?): String {
        return if (phrase.isNullOrBlank()) {
            this
        } else {
            replace(Regex("\\b${Regex.escape(phrase)}\\b", RegexOption.IGNORE_CASE), " ")
        }
    }

    private fun String.removeLocationPhrase(location: String?): String {
        return if (location.isNullOrBlank()) {
            this
        } else {
            replace(Regex("\\b(at|in|near)\\s+${Regex.escape(location)}\\b", RegexOption.IGNORE_CASE), " ")
        }
    }

    private fun String.removeTrailingConnectors(): String {
        return replace(CONNECTOR_PATTERN, " ")
    }

    private fun String.cleanPhrase(): String {
        return normalizeWhitespace()
            .trim(' ', ',', '-', ':', '|', '@')
    }

    private fun String.normalizeWhitespace(): String {
        return trim()
            .replace("@", " at ")
            .replace(WHITESPACE_PATTERN, " ")
    }

    private val WHITESPACE_PATTERN = Regex("\\s+")
    private val CONNECTOR_PATTERN = Regex("\\b(at|in|near|on|by)\\b\\s*$", RegexOption.IGNORE_CASE)
    private val TIME_PATTERN = Regex(
        "\\b(\\d{1,2}([:.]\\d{2}|\\s+\\d{2})\\s?(am|pm)?|\\d{1,2}\\s?(am|pm)|noon|midnight)\\b",
        RegexOption.IGNORE_CASE
    )
    private val DATE_PATTERN = Regex(
        "\\b(today|tonight|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
            "mon|tue|wed|thu|fri|sat|sun|jan\\.?|feb\\.?|mar\\.?|apr\\.?|may|jun\\.?|jul\\.?|" +
            "aug\\.?|sep\\.?|sept\\.?|oct\\.?|nov\\.?|dec\\.?|\\d{1,2}/\\d{1,2}(/\\d{2,4})?|" +
            "\\d{1,2}-\\d{1,2}(-\\d{2,4})?)\\b",
        RegexOption.IGNORE_CASE
    )
    private val LOCATION_PATTERN = Regex(
        "\\b(?:at|in|near)\\s+([\\p{L}][\\p{L}\\p{N}'&.-]*(?:\\s+[\\p{L}\\p{N}][\\p{L}\\p{N}'&.-]*){0,4})",
        RegexOption.IGNORE_CASE
    )
}

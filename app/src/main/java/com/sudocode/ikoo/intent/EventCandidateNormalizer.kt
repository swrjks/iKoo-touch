package com.sudocode.ikoo.intent

import com.sudocode.ikoo.core.ai.AIEngine

object EventCandidateNormalizer {
    suspend fun normalizeWithAi(text: String, engine: AIEngine?): EventData? {
        val fallback = strictLocalEvent(text)
        if (engine == null || !engine.isReady()) return fallback

        val response = engine.generate(buildPrompt(text), maxTokens = 140)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotBlank() }
            .orEmpty()

        if (response.equals("NONE", ignoreCase = true)) return null

        val payload = response.removePrefix("EVENT:").trim()
        val parsed = parseStructuredPayload(payload) ?: EventExtractor.extract(payload)
        return parsed?.takeIf { isStrictEvent(it) } ?: fallback
    }

    fun strictLocalEvent(text: String): EventData? {
        if (text.isUiNoise() || text.isFinancialNoise()) return null
        val result = IntentDetector.detect(text)
        if (result.type != IntentType.CALENDAR_EVENT) return null
        return EventExtractor.extract(result.matchedText)
            ?.takeIf { isStrictEvent(it) }
    }

    fun isStrictEvent(eventData: EventData): Boolean {
        return eventData.isStrictEventInternal()
    }

    fun cleaned(eventData: EventData): EventData {
        return eventData.cleanedInternal()
    }

    private fun EventData.isStrictEventInternal(): Boolean {
        val cleanTitle = title.cleanTitle()
        if (cleanTitle.length < 3) return false
        if (timePhrase.isNullOrBlank()) return false
        if (cleanTitle.isUiNoise() || cleanTitle.isFinancialNoise()) return false
        val combined = "$cleanTitle ${datePhrase.orEmpty()} ${timePhrase.orEmpty()}".lowercase()
        return EVENT_WORDS.any { it in combined }
    }

    private fun EventData.cleanedInternal(): EventData {
        return copy(
            title = title.cleanTitle(),
            datePhrase = datePhrase?.cleanField(),
            timePhrase = timePhrase?.cleanField(),
            location = location?.cleanField()
        )
    }

    private fun buildPrompt(text: String): String {
        return """
            You are iKoo's offline calendar event cleaner.
            Decide if the text contains a real calendar event, meeting, meal, call, appointment, exam, class, party, or reservation.
            Ignore app UI text, Gmail controls, sender controls, bank/UPI/account/transaction messages, notification counts, and random timestamps.

            Return exactly NONE if there is no real event.
            Otherwise return exactly one line in this format:
            EVENT: title=<short event title>; date=<date phrase or blank>; time=<time phrase>; location=<location or blank>

            The time must be the event time, not the email received time.
            Text:
            $text
        """.trimIndent()
    }

    private fun parseStructuredPayload(payload: String): EventData? {
        val parts = payload.split(";")
            .mapNotNull { part ->
                val key = part.substringBefore("=", "").trim().lowercase()
                val value = part.substringAfter("=", "").trim()
                if (key.isBlank()) null else key to value
            }
            .toMap()
        val title = parts["title"]?.cleanTitle().orEmpty()
        val time = parts["time"]?.cleanField().orEmpty()
        if (title.isBlank() || time.isBlank()) return null
        return EventData(
            title = title,
            datePhrase = parts["date"]?.cleanField()?.ifBlank { null },
            timePhrase = time,
            location = parts["location"]?.cleanField()?.ifBlank { null },
            confidence = 0.92f
        )
    }

    private fun String.cleanTitle(): String {
        return replace(Regex("\\s+"), " ")
            .replace(Regex("\\b(show contact information|add emoji reaction|smart reply|tap to edit|reply|more options|to me)\\b.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(search in mail|open navigation drawer|signed in as|google one|inbox)\\b.*", RegexOption.IGNORE_CASE), "")
            .trim(' ', '-', ':', '|', ',', '.')
            .take(64)
            .trim()
    }

    private fun String.cleanField(): String {
        return replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', '|', ',', '.')
    }

    private fun String.isUiNoise(): Boolean {
        val text = lowercase()
        return UI_NOISE.any { it in text }
    }

    private fun String.isFinancialNoise(): Boolean {
        val text = lowercase()
        return FINANCIAL_NOISE.any { it in text }
    }

    private val EVENT_WORDS = listOf(
        "meeting", "meet", "dinner", "lunch", "breakfast", "call", "appointment",
        "interview", "exam", "class", "party", "reservation", "session", "visit"
    )

    private val UI_NOISE = listOf(
        "search in mail", "open navigation drawer", "signed in as", "google one",
        "show contact information", "add emoji reaction", "smart reply", "tap to edit",
        "more options", "reply more", "to me", "show calendar list", "settings drawer",
        "jump to today", "add birthdays", "be reminded"
    )

    private val FINANCIAL_NOISE = listOf(
        "account number", "transaction info", "upi", "debited", "credited",
        "bank", "balance", "compose mail", "more than 99 new notifications"
    )
}

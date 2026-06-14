package com.sudocode.ikoo.pipeline

import android.content.Context
import com.sudocode.ikoo.core.ai.AIEngineRegistry
import com.sudocode.ikoo.history.HistoryRepository
import com.sudocode.ikoo.intent.EventCandidateNormalizer
import com.sudocode.ikoo.intent.IntentDetectionStore
import com.sudocode.ikoo.intent.IntentDetectionResult
import com.sudocode.ikoo.intent.IntentType
import com.sudocode.ikoo.intent.LatestIntentDetection
import com.sudocode.ikoo.overlay.OverlaySuggestionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object IKooPipeline {
    private val pipelineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun process(
        context: Context? = null,
        packageName: String,
        visibleText: String,
        detectedAtMillis: Long = System.currentTimeMillis(),
        overlaySuggestionManager: OverlaySuggestionManager? = null
    ): LatestIntentDetection {
        val detection = IntentDetectionStore.update(
            packageName = packageName,
            visibleText = visibleText,
            detectedAtMillis = detectedAtMillis
        )
        context?.let {
            HistoryRepository.getInstance(it).saveDetection(detection)
        }

        if (packageName.isSupportedPackage() && visibleText.hasEventCandidateSignal()) {
            if (context != null) {
                refineWithGemma(
                    context = context.applicationContext,
                    packageName = packageName,
                    visibleText = visibleText,
                    detectedAtMillis = detectedAtMillis,
                    overlaySuggestionManager = overlaySuggestionManager
                )
            } else {
                detection.eventData
                    ?.takeIf { EventCandidateNormalizer.isStrictEvent(it) }
                    ?.let { EventCandidateNormalizer.cleaned(it) }
                    ?.let { event ->
                        overlaySuggestionManager?.showCalendarSuggestion(
                            eventData = event,
                            originalText = detection.result.matchedText
                        )
                    }
            }
        }

        return detection
    }

    private fun refineWithGemma(
        context: Context,
        packageName: String,
        visibleText: String,
        detectedAtMillis: Long,
        overlaySuggestionManager: OverlaySuggestionManager?
    ) {
        pipelineScope.launch {
            AIEngineRegistry.initialize(context)
            val event = EventCandidateNormalizer
                .normalizeWithAi(visibleText, AIEngineRegistry.active())
                ?.let { EventCandidateNormalizer.cleaned(it) }
                ?: return@launch

            val refined = LatestIntentDetection(
                packageName = packageName,
                visibleText = visibleText,
                detectedAtMillis = detectedAtMillis,
                result = IntentDetectionResult(
                    type = IntentType.CALENDAR_EVENT,
                    confidence = event.confidence.coerceAtLeast(0.86f),
                    matchedText = listOfNotNull(event.title, event.datePhrase, event.timePhrase, event.location)
                        .joinToString(" "),
                    reason = "Clean calendar event confirmed by offline Gemma."
                ),
                eventData = event,
                latencyMillis = 0L,
                offline = true
            )

            IntentDetectionStore.publish(refined)
            HistoryRepository.getInstance(context).saveDetection(refined)
            overlaySuggestionManager?.showCalendarSuggestion(
                eventData = event,
                originalText = refined.result.matchedText
            )
        }
    }

    private fun String.isSupportedPackage(): Boolean = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.google.android.gm",
        "com.microsoft.teams",
        "com.google.android.apps.messaging",
        "org.telegram.messenger",
        "com.slack"
    ).any { contains(it, ignoreCase = true) }

    private fun String.hasEventCandidateSignal(): Boolean {
        val text = lowercase()
        if (listOf("account number", "transaction info", "upi", "search in mail", "open navigation drawer").any { it in text }) {
            return false
        }
        val hasEventWord = listOf(
            "meeting", "meet", "dinner", "lunch", "breakfast", "call", "appointment",
            "interview", "exam", "class", "party", "reservation", "session", "visit"
        ).any { it in text }
        val hasTime = Regex("\\b(\\d{1,2}([:.]\\d{2}|\\s+\\d{2})\\s?(am|pm)?|\\d{1,2}\\s?(am|pm)|noon|midnight)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(this)
        return hasEventWord && hasTime
    }
}

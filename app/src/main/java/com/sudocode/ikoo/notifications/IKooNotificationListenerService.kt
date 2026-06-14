package com.sudocode.ikoo.notifications

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sudocode.ikoo.core.ai.AIEngineRegistry
import com.sudocode.ikoo.intent.IntentDetector
import com.sudocode.ikoo.intent.IntentType
import com.sudocode.ikoo.overlay.OverlaySuggestionManager
import com.sudocode.ikoo.pipeline.IKooPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Monitors incoming notifications from supported apps (WhatsApp, Gmail,
 * Teams) and feeds their text content through [IKooPipeline] — the same
 * intent-detection pipeline used by [com.sudocode.ikoo.accessibility.IKooAccessibilityService] —
 * so a "Meeting tomorrow 3pm" notification produces the same
 * "Add to Calendar" overlay suggestion as detecting it on-screen.
 *
 * Requires the user to grant Notification Access
 * (Settings > Apps > Special app access > Notification access > iKoo).
 */
open class IKooNotificationListenerService : NotificationListenerService() {

    private lateinit var overlaySuggestionManager: OverlaySuggestionManager
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        overlaySuggestionManager = OverlaySuggestionManager(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return

        if (!SUPPORTED_PACKAGES.any { packageName.contains(it, ignoreCase = true) }) return

        val notificationText = extractNotificationText(sbn.notification)
        val bestCandidate = notificationText.bestCalendarCandidate()
        if (bestCandidate.text.isBlank()) return

        if (bestCandidate.score >= MIN_NOTIFICATION_EVENT_SCORE) {
            processCandidate(packageName, bestCandidate, sbn.postTime)
            return
        }

        if (!notificationText.hasEventHint()) return

        notificationScope.launch {
            val aiCandidate = notificationText.gemmaCalendarCandidate()
            processCandidate(packageName, aiCandidate ?: bestCandidate, sbn.postTime)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op: overlay suppression already handles repeated notifications.
    }

    override fun onDestroy() {
        notificationScope.cancel()
        super.onDestroy()
    }

    private fun processCandidate(
        packageName: String,
        candidate: NotificationCandidate,
        postTime: Long
    ) {
        val detection = IKooPipeline.process(
            context = applicationContext,
            packageName = packageName,
            visibleText = candidate.text,
            detectedAtMillis = postTime,
            overlaySuggestionManager = overlaySuggestionManager
        )

        Log.i(
            TAG,
            "notification package=$packageName intent=${detection.result.type} " +
                "confidence=${"%.2f".format(detection.result.confidence)} " +
                "source=${candidate.source} score=${"%.2f".format(candidate.score)} text=${candidate.text}"
        )
    }

    private fun extractNotificationText(notification: Notification): NotificationText {
        val extras = notification.extras
        val subject = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString().orEmpty()
        ).bestText()
        val body = listOf(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
        ).bestText()
        val lines = buildList {
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.forEach { add(it.toString()) }
        }.cleanDistinct()

        val messageText = extractMessagingStyleText(extras)
        val allExtraText = extras.keySet()
            .flatMap { key -> extras.get(key).flattenExtraValue() }
            .cleanDistinct()

        return NotificationText(
            subject = subject,
            body = body,
            lines = lines,
            messages = messageText.cleanDistinct(),
            extras = allExtraText
        )
    }

    private fun extractMessagingStyleText(extras: Bundle): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return emptyList()
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        return Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages)
            ?.flatMap { message ->
                listOfNotNull(
                    message.senderPerson?.name?.toString(),
                    message.sender?.toString(),
                    message.text?.toString()
                )
            }
            .orEmpty()
    }

    private fun Any?.flattenExtraValue(): List<String> {
        return when (this) {
            null -> emptyList()
            is CharSequence -> listOf(toString())
            is Array<*> -> flatMap { it.flattenExtraValue() }
            is Iterable<*> -> flatMap { it.flattenExtraValue() }
            else -> emptyList()
        }
    }

    private fun String.normalizeNotificationText(): String {
        return replace(Regex("\\s+"), " ")
            .replace("·", " ")
            .trim()
    }

    private fun List<String>.bestText(): String {
        return cleanDistinct().maxByOrNull { it.length }.orEmpty()
    }

    private fun List<String>.cleanDistinct(): List<String> {
        return map { it.normalizeNotificationText() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun NotificationText.bestCalendarCandidate(): NotificationCandidate {
        val candidates = buildList {
            add(NotificationCandidate("subject", subject))
            add(NotificationCandidate("body", body))
            lines.forEachIndexed { index, line -> add(NotificationCandidate("line_$index", line)) }
            messages.forEachIndexed { index, message -> add(NotificationCandidate("message_$index", message)) }
            add(NotificationCandidate("subject_body", listOf(subject, body).joinClean()))
            add(NotificationCandidate("all", allText()))
            extras.forEachIndexed { index, extra -> add(NotificationCandidate("extra_$index", extra)) }
        }.filter { it.text.isNotBlank() }

        return candidates
            .map { candidate -> candidate.copy(score = candidate.calendarScore()) }
            .maxByOrNull { it.score }
            ?: NotificationCandidate("all", allText(), score = 0f)
    }

    private fun NotificationCandidate.calendarScore(): Float {
        val result = IntentDetector.detect(text)
        if (result.type != IntentType.CALENDAR_EVENT) return 0f
        if (UI_NOISE_PATTERN.containsMatchIn(text)) return 0f
        val sourceBonus = when (source) {
            "subject" -> 0.22f
            "subject_body" -> 0.12f
            else -> 0f
        }
        val conciseBonus = if (text.length <= 90) 0.06f else 0f
        return (result.confidence + sourceBonus + conciseBonus).coerceAtMost(1f)
    }

    private fun NotificationText.allText(): String {
        return (listOf(subject, body) + lines + messages + extras).joinClean()
    }

    private fun NotificationText.hasEventHint(): Boolean {
        return EVENT_HINT_PATTERN.containsMatchIn(allText())
    }

    private suspend fun NotificationText.gemmaCalendarCandidate(): NotificationCandidate? {
        AIEngineRegistry.initialize(applicationContext)
        val engine = AIEngineRegistry.active()
        if (engine == null || !engine.initialize()) return null
        val response = engine.generate(buildGemmaPrompt(), maxTokens = 180)?.trim().orEmpty()
        val extracted = response
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.equals("NONE", ignoreCase = true) }
            ?.removePrefix("EVENT:")
            ?.trim()
            .orEmpty()
        if (extracted.isBlank()) return null
        val score = NotificationCandidate("gemma", extracted).calendarScore()
        return NotificationCandidate("gemma", extracted, score)
            .takeIf { it.score >= MIN_AI_EVENT_SCORE }
    }

    private fun NotificationText.buildGemmaPrompt(): String {
        return """
            You are an offline calendar intent extractor.
            Decide if this notification describes a real calendar event, appointment, meal, call, exam, meeting, or reminder with a time/date.
            If it is not calendar-worthy, return exactly: NONE
            If it is calendar-worthy, return one short line:
            EVENT: <title> <date phrase if present> <time phrase if present> <location if present>

            Prefer the email subject if it already contains the event and time.
            Keep Indian/English time phrases like 10 30 AM or 2:30 PM.

            Subject: ${subject}
            Body: ${body}
            Lines: ${lines.joinToString(" | ")}
            Messages: ${messages.joinToString(" | ")}
        """.trimIndent()
    }

    private fun List<String>.joinClean(): String {
        return cleanDistinct().joinToString(" ").trim()
    }

    private data class NotificationText(
        val subject: String,
        val body: String,
        val lines: List<String>,
        val messages: List<String>,
        val extras: List<String>
    )

    private data class NotificationCandidate(
        val source: String,
        val text: String,
        val score: Float = 0f
    )

    private companion object {
        const val TAG = "IKooNotifications"

        val SUPPORTED_PACKAGES = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.google.android.gm", // Gmail
            "com.microsoft.teams",
            "com.google.android.apps.messaging",
            "org.telegram.messenger",
            "com.slack"
        )
        const val MIN_NOTIFICATION_EVENT_SCORE = 0.55f
        const val MIN_AI_EVENT_SCORE = 0.50f
        val EVENT_HINT_PATTERN = Regex(
            "\\b(meeting|dinner|lunch|breakfast|call|appointment|interview|exam|class|party|reservation|" +
                "today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday|" +
                "\\d{1,2}([:.]\\d{2}|\\s+\\d{2})\\s?(am|pm)?|\\d{1,2}\\s?(am|pm))\\b",
            RegexOption.IGNORE_CASE
        )
        val UI_NOISE_PATTERN = Regex(
            "\\b(search in mail|open navigation drawer|signed in as|google one|inbox|reply|smart reply|" +
                "show contact information|add emoji reaction|account number|transaction info|upi|debited|credited)\\b",
            RegexOption.IGNORE_CASE
        )
    }
}

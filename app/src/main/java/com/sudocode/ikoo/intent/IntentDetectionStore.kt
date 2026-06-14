package com.sudocode.ikoo.intent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LatestIntentDetection(
    val packageName: String,
    val visibleText: String,
    val detectedAtMillis: Long,
    val result: IntentDetectionResult,
    val eventData: EventData?,
    val latencyMillis: Long,
    val offline: Boolean
)

object IntentDetectionStore {
    private val emptyResult = LatestIntentDetection(
        packageName = "",
        visibleText = "",
        detectedAtMillis = 0L,
        result = IntentDetectionResult(
            type = IntentType.NONE,
            confidence = 0f,
            matchedText = "",
            reason = "Ready for screen text."
        ),
        eventData = null,
        latencyMillis = 0L,
        offline = true
    )
    private val mutableLatestDetection = MutableStateFlow(emptyResult)
    private val mutableDetectionHistory = MutableStateFlow<List<LatestIntentDetection>>(emptyList())

    val latestDetection: StateFlow<LatestIntentDetection> = mutableLatestDetection.asStateFlow()
    val detectionHistory: StateFlow<List<LatestIntentDetection>> = mutableDetectionHistory.asStateFlow()

    fun update(
        packageName: String,
        visibleText: String,
        detectedAtMillis: Long = System.currentTimeMillis()
    ): LatestIntentDetection {
        val current = mutableLatestDetection.value
        if (
            current.packageName == packageName &&
            current.visibleText == visibleText &&
            detectedAtMillis - current.detectedAtMillis < DUPLICATE_SUPPRESSION_MILLIS
        ) {
            return current
        }

        val startedAtNanos = System.nanoTime()
        val result = IntentDetector.detect(visibleText)
        val eventData = if (result.type == IntentType.CALENDAR_EVENT) {
            EventExtractor.extract(result.matchedText)
        } else {
            null
        }
        val latencyMillis = ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)
        val detection = LatestIntentDetection(
            packageName = packageName,
            visibleText = visibleText,
            detectedAtMillis = detectedAtMillis,
            result = result,
            eventData = eventData,
            latencyMillis = latencyMillis,
            offline = true
        )
        publish(detection)
        return detection
    }

    fun publish(detection: LatestIntentDetection) {
        mutableLatestDetection.value = detection
        if (detection.visibleText.isNotBlank()) {
            mutableDetectionHistory.value = (listOf(detection) + mutableDetectionHistory.value)
                .take(MAX_HISTORY_SIZE)
        }
    }

    private const val MAX_HISTORY_SIZE = 20
    private const val DUPLICATE_SUPPRESSION_MILLIS = 15_000L
}

package com.sudocode.ikoo.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sudocode.ikoo.calendar.CalendarActionManager
import com.sudocode.ikoo.core.ai.AIEngineRegistry
import com.sudocode.ikoo.history.HistoryRepository
import com.sudocode.ikoo.intent.EventCandidateNormalizer
import com.sudocode.ikoo.intent.IntentDetectionResult
import com.sudocode.ikoo.intent.IntentDetectionStore
import com.sudocode.ikoo.intent.IntentType
import com.sudocode.ikoo.intent.LatestIntentDetection
import com.sudocode.ikoo.overlay.OverlaySuggestionManager
import com.sudocode.ikoo.pipeline.IKooPipeline
import com.sudocode.ikoo.usage.AppUsageLimiter
import com.sudocode.ikoo.usage.UsageLimitWarningActivity
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IKooAccessibilityService : AccessibilityService() {
    private var lastLoggedText: String = ""
    private var lastLoggedAtMillis: Long = 0L
    private lateinit var overlaySuggestionManager: OverlaySuggestionManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    private var lastUsageWarningAtMillis: Long = 0L

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SCAN_CURRENT_SCREEN -> scanCurrentScreen(force = true)
                ACTION_PRODUCT_SCREENSHOT_SEARCH -> captureScreenshotForProductSearch()
                ACTION_EXIT_LIMITED_APP -> performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        overlaySuggestionManager = OverlaySuggestionManager(this)
        ContextCompat.registerReceiver(
            this,
            scanReceiver,
            IntentFilter().apply {
                addAction(ACTION_SCAN_CURRENT_SCREEN)
                addAction(ACTION_PRODUCT_SCREENSHOT_SEARCH)
                addAction(ACTION_EXIT_LIMITED_APP)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !event.isSupportedTextEvent()) return
        event.packageName?.toString()?.let(::enforceUsageLimit)

        scanCurrentScreen(
            force = false,
            packageNameOverride = event.packageName?.toString()
        )
    }

    private fun scanCurrentScreen(force: Boolean, packageNameOverride: String? = null) {
        val rootNode = rootInActiveWindow ?: return
        val visibleText = rootNode.extractVisibleText()
        if (visibleText.isBlank()) return

        val now = System.currentTimeMillis()
        if (!force && visibleText == lastLoggedText && now - lastLoggedAtMillis < DUPLICATE_WINDOW_MILLIS) {
            return
        }

        lastLoggedText = visibleText
        lastLoggedAtMillis = now

        val packageName = packageNameOverride
            ?: rootNode.packageName?.toString()
            ?: "screen_scan"

        val detection = IKooPipeline.process(
            context = applicationContext,
            packageName = packageName,
            visibleText = visibleText,
            detectedAtMillis = now,
            overlaySuggestionManager = overlaySuggestionManager
        )

        Log.i(
            TAG,
            "package=$packageName, " +
                    "timestamp=${timestampFormat.format(Date(now))}, " +
                    "visibleText=$visibleText"
        )

        Log.i(
            TAG,
            "intent=${detection.result.type}, " +
                    "confidence=${"%.2f".format(Locale.US, detection.result.confidence)}, " +
                    "matchedText=${detection.result.matchedText}, " +
                    "reason=${detection.result.reason}"
        )
    }

    override fun onInterrupt() {
        Log.i(TAG, "iKoo accessibility monitoring interrupted")
    }

    override fun onDestroy() {
        overlaySuggestionManager.hide(immediate = true)
        runCatching { unregisterReceiver(scanReceiver) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun AccessibilityEvent.isSupportedTextEvent(): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    }

    private fun enforceUsageLimit(packageName: String) {
        if (packageName == this.packageName) return

        val limit = AppUsageLimiter.readLimit(this) ?: return
        if (limit.packageName != packageName) return

        val now = System.currentTimeMillis()
        if (now - lastUsageWarningAtMillis < USAGE_WARNING_COOLDOWN_MILLIS) return

        val usedMillis = runCatching {
            AppUsageLimiter.todayUsageMillis(this, packageName)
        }.getOrDefault(0L)

        if (usedMillis < limit.limitMillis) return

        lastUsageWarningAtMillis = now
        val appName = packageName.readableAppName()

        val intent = Intent(this, UsageLimitWarningActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(UsageLimitWarningActivity.EXTRA_APP_NAME, appName)
            putExtra(UsageLimitWarningActivity.EXTRA_LIMIT_MINUTES, limit.limitMinutes)
        }

        runCatching { startActivity(intent) }
    }

    private fun AccessibilityNodeInfo.extractVisibleText(): String {
        val capturedText = LinkedHashSet<String>()
        collectVisibleText(capturedText)
        return capturedText.joinToString(separator = " ").normalizeForLog()
    }

    private fun AccessibilityNodeInfo.collectVisibleText(capturedText: MutableSet<String>) {
        if (!isVisibleToUser) return

        text?.toString()
            ?.normalizeForLog()
            ?.takeIf { it.isNotBlank() }
            ?.let(capturedText::add)

        contentDescription?.toString()
            ?.normalizeForLog()
            ?.takeIf { it.isNotBlank() }
            ?.let(capturedText::add)

        for (index in 0 until childCount) {
            getChild(index)?.collectVisibleText(capturedText)
        }
    }

    private fun String.normalizeForLog(): String {
        return trim().replace(WHITESPACE_REGEX, " ")
    }

    private fun captureScreenshotForProductSearch() {
        showToast("Capturing screenshot...")
        val visibleText = rootInActiveWindow?.extractVisibleText().orEmpty()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            showToast("Screenshot search needs Android 11 or newer.")
            openBestPriceSearch(visibleText.toLocalProductQuery() ?: "search product by image")
            return
        }

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            ContextCompat.getMainExecutor(this),
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer = screenshot.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()

                    if (bitmap == null) {
                        showToast("Could not capture screenshot.")
                        return
                    }

                    val uri = runCatching { saveScreenshot(bitmap) }.getOrNull()

                    if (uri == null) {
                        bitmap.recycle()
                        showToast("Could not prepare screenshot search.")
                    } else {
                        showToast("Opening Google Lens...")
                        openVisualProductSearch(uri)
                        bitmap.recycle()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "screenshot failed: $errorCode")
                    showToast("Screenshot blocked. Re-enable iKoo Accessibility.")
                    openBestPriceSearch(visibleText.toLocalProductQuery() ?: "search product by image")
                }
            }
        )
    }

    private fun saveScreenshot(bitmap: Bitmap): Uri {
        val screenshotsDir = File(cacheDir, "ikoo_screenshots").apply { mkdirs() }
        val file = File(screenshotsDir, "product_${System.currentTimeMillis()}.jpg")

        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        }

        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    private fun openSmartScreenshotAction(visibleText: String, screenshotUri: Uri, bitmap: Bitmap) {
        serviceScope.launch {
            try {
                val screenshotText = readTextFromScreenshot(bitmap)
                val combinedText = listOf(visibleText, screenshotText)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .normalizeForLog()

                val event = detectCalendarEventFromScreenshot(combinedText)

                if (event != null) {
                    showToast("Event found. Opening Calendar...")
                    publishScreenshotEvent(combinedText, event)
                    CalendarActionManager.openCalendarInsert(applicationContext, event)
                } else {
                    openOptimizedProductSearch(combinedText, screenshotUri)
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private suspend fun readTextFromScreenshot(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            Tasks.await(TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image))
                .text
                .normalizeForLog()
        }.getOrDefault("")
    }

    private suspend fun detectCalendarEventFromScreenshot(text: String): com.sudocode.ikoo.intent.EventData? {
        if (text.isBlank()) return null

        AIEngineRegistry.initialize(applicationContext)

        return EventCandidateNormalizer
            .normalizeWithAi(text, AIEngineRegistry.active())
            ?.let { EventCandidateNormalizer.cleaned(it) }
    }

    private fun publishScreenshotEvent(text: String, event: com.sudocode.ikoo.intent.EventData) {
        val detection = LatestIntentDetection(
            packageName = "screenshot.scan",
            visibleText = text,
            detectedAtMillis = System.currentTimeMillis(),
            result = IntentDetectionResult(
                type = IntentType.CALENDAR_EVENT,
                confidence = event.confidence.coerceAtLeast(0.9f),
                matchedText = listOfNotNull(
                    event.title,
                    event.datePhrase,
                    event.timePhrase,
                    event.location
                ).joinToString(" "),
                reason = "Calendar event detected from screenshot."
            ),
            eventData = event,
            latencyMillis = 0L,
            offline = true
        )

        IntentDetectionStore.publish(detection)
        HistoryRepository.getInstance(applicationContext).saveDetection(detection)
    }

    private fun openOptimizedProductSearch(visibleText: String, screenshotUri: Uri) {
        serviceScope.launch {
            val productQuery = extractProductQueryWithGemma(visibleText)
                ?: visibleText.toLocalProductQuery()

            if (productQuery != null) {
                showToast("Searching best price...")
                openBestPriceSearch(productQuery)
            } else {
                showToast("Image-only product. Opening visual search...")
                openVisualProductSearch(screenshotUri)
            }
        }
    }

    private suspend fun extractProductQueryWithGemma(visibleText: String): String? {
        val cleanedInput = visibleText.cleanProductText()
        if (cleanedInput.length < MIN_PRODUCT_TEXT_LENGTH) return null

        val localGuess = cleanedInput.toLocalProductQuery()

        AIEngineRegistry.initialize(applicationContext)

        val engine = AIEngineRegistry.active().takeIf { it?.isReady() == true } ?: return localGuess

        val response = engine.generate(
            """
            Extract the product name from this shopping screen text for best-price search.
            Return only the product name. If there is no product, return NONE.
            Text: $cleanedInput
            """.trimIndent(),
            maxTokens = 48
        )?.trim()

        return response
            ?.lineSequence()
            ?.firstOrNull()
            ?.cleanProductText()
            ?.takeIf {
                it.length >= MIN_PRODUCT_TEXT_LENGTH &&
                        !it.equals("NONE", ignoreCase = true)
            }
            ?: localGuess
    }

    private fun openBestPriceSearch(productQuery: String) {
        val encodedQuery = URLEncoder.encode("$productQuery best price buy online", "UTF-8")

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?tbm=shop&q=$encodedQuery")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching { startActivity(intent) }
            .onFailure { openVisualSearchWebFallback() }
    }

    private fun openVisualProductSearch(uri: Uri) {
        val packages = listOf(
            "com.google.android.googlequicksearchbox",
            "com.google.ar.lens"
        )

        for (packageName in packages) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                setPackage(packageName)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Find this product best price")
                clipData = ClipData.newUri(contentResolver, "iKoo product screenshot", uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (runCatching { startActivity(intent) }.isSuccess) return
        }

        openVisualSearchWebFallback()
    }

    private fun String.toLocalProductQuery(): String? {
        val cleaned = cleanProductText()

        val tokens = PRODUCT_TOKEN_REGEX.findAll(cleaned)
            .map { it.value }
            .filterNot { token -> token.lowercase(Locale.US) in PRODUCT_STOP_WORDS }
            .take(12)
            .toList()

        return tokens
            .joinToString(" ")
            .takeIf { it.length >= MIN_PRODUCT_TEXT_LENGTH && tokens.size >= 2 }
    }

    private fun String.cleanProductText(): String {
        return replace(URL_REGEX, " ")
            .replace(PRODUCT_NOISE_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .take(MAX_PRODUCT_TEXT_LENGTH)
    }

    private fun openVisualSearchWebFallback() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://lens.google.com/")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching { startActivity(intent) }
            .onFailure {
                val fallback = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=search+product+by+image")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                runCatching { startActivity(fallback) }
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val TAG = "IKooAccessibility"
        const val ACTION_SCAN_CURRENT_SCREEN = "com.sudocode.ikoo.SCAN_CURRENT_SCREEN"
        const val ACTION_PRODUCT_SCREENSHOT_SEARCH = "com.sudocode.ikoo.PRODUCT_SCREENSHOT_SEARCH"
        const val ACTION_EXIT_LIMITED_APP = "com.sudocode.ikoo.EXIT_LIMITED_APP"

        private const val MIN_PRODUCT_TEXT_LENGTH = 8
        private const val MAX_PRODUCT_TEXT_LENGTH = 260
        private const val DUPLICATE_WINDOW_MILLIS = 15_000L
        private const val USAGE_WARNING_COOLDOWN_MILLIS = 60_000L

        private val WHITESPACE_REGEX = Regex("\\s+")
        private val URL_REGEX = Regex("""(?i)\b(?:https?://|www\.|[a-z0-9-]+\.(?:com|in|net|org))\S*""")
        private val PRODUCT_TOKEN_REGEX = Regex("""[A-Za-z0-9][A-Za-z0-9+.\-']*""")

        private val PRODUCT_NOISE_REGEX = Regex(
            """(?i)\b(?:home|back|share|search|cart|menu|stores|store|offers?|off|coupon|""" +
                    """get app|open app|login|sign in|new tab|more|options|delivery|add|buy now|""" +
                    """swiggy|amazon|flipkart|myntra|meesho|google|chrome|http|www)\b"""
        )

        private val PRODUCT_STOP_WORDS = setOf(
            "the",
            "and",
            "for",
            "with",
            "from",
            "this",
            "that",
            "your",
            "best",
            "price",
            "online",
            "item",
            "product",
            "screen",
            "image"
        )
    }

    private fun String.readableAppName(): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(this, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(substringAfterLast('.'))
    }
}
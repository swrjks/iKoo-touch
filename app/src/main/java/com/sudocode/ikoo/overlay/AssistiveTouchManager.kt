package com.sudocode.ikoo.overlay

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.sudocode.ikoo.accessibility.IKooAccessibilityService
import com.sudocode.ikoo.assistant.VoiceOverlayActivity
import com.sudocode.ikoo.calendar.CalendarActionManager
import com.sudocode.ikoo.core.ai.AIEngineRegistry
import com.sudocode.ikoo.history.HistoryRepository
import com.sudocode.ikoo.intent.EventData
import com.sudocode.ikoo.intent.IntentDetectionStore
import com.sudocode.ikoo.intent.IntentType
import com.sudocode.ikoo.intent.LatestIntentDetection
import com.sudocode.ikoo.wakeword.HeyIKooService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

class AssistiveTouchManager(
    private val context: Context
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var touchView: View? = null
    private var panelOpen = false
    private var scanning = false
    private var askStatus: String = "Ask iKoo to detect meetings, reminders, dinners, or tasks."
    private val assistantScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bubbleX = -1
    private var bubbleY = -1
    private val dismissedSuggestionKeys = LinkedHashSet<String>()
    private var wakeWordReceiverRegistered = false

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == HeyIKooService.ACTION_WAKE_WORD_DETECTED) {
                scanning = true
                animateWakeWord()
            }
        }
    }

    private fun animateWakeWord() {
        touchView?.animate()
            ?.scaleX(1.25f)
            ?.scaleY(1.25f)
            ?.setDuration(150L)
            ?.withEndAction {
                touchView?.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(250L)
                    ?.start()
            }
            ?.start()
    }

    fun show(): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        if (touchView != null) return true

        panelOpen = false
        scanning = false

        val button = buildFloatingButton()
        val params = floatingLayoutParams()

        touchView = button
        windowManager.addView(button, params)
        registerWakeWordReceiver()

        button.scaleX = 0.8f
        button.scaleY = 0.8f
        button.alpha = 0f

        button.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        return true
    }

    fun hide() {
        removeCurrentView()
        unregisterWakeWordReceiver()
    }

    fun isShowing(): Boolean = touchView != null

    private fun expandPanel() {
        requestCurrentScreenScan()
        removeCurrentView()
        mainHandler.postDelayed({ showPanelNow() }, PANEL_SCAN_DELAY_MILLIS)
    }

    private fun showPanelNow() {
        panelOpen = true

        val panel = buildPanelOverlay()
        touchView = panel

        windowManager.addView(panel, panelLayoutParams())
        registerWakeWordReceiver()

        panel.alpha = 0f
        panel.scaleX = 0.95f
        panel.scaleY = 0.95f
        panel.translationY = -30.dp.toFloat()

        panel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(300L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun collapsePanel() {
        touchView?.animate()
            ?.alpha(0f)
            ?.scaleX(0.95f)
            ?.scaleY(0.95f)
            ?.setDuration(180L)
            ?.withEndAction {
                removeCurrentView()
                show()
            }
            ?.start()
    }

    private fun removeCurrentView() {
        val view = touchView ?: return
        touchView = null
        runCatching { windowManager.removeView(view) }
    }

    private fun launchVoiceOverlay() {
        scanning = true
        context.sendBroadcast(Intent(HeyIKooService.ACTION_WAKE_WORD_DETECTED))

        val intent = Intent(context, VoiceOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        context.startActivity(intent)
    }

    private fun launchMainApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        if (intent != null) context.startActivity(intent)
    }

    private fun buildFloatingButton(): View {
        return FrameLayout(context).apply {
            setPadding(6.dp, 6.dp, 6.dp, 6.dp)
            background = premiumOutlineBackground(oval = true)
            elevation = 32.dp.toFloat()

            addView(createGlowRingView(), FrameLayout.LayoutParams(64.dp, 64.dp, Gravity.CENTER))

            addView(
                View(context).apply {
                    background = iqooOrbBackground()
                },
                FrameLayout.LayoutParams(52.dp, 52.dp, Gravity.CENTER)
            )

            addView(
                TextView(context).apply {
                    text = "iQ"
                    gravity = Gravity.CENTER
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    background = centerIconBackground()
                },
                FrameLayout.LayoutParams(32.dp, 32.dp, Gravity.CENTER)
            )

            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            var downRawX = 0f
            var downRawY = 0f
            var startX = 0
            var startY = 0
            var dragging = false

            setOnTouchListener { view, event ->
                val params = view.layoutParams as? WindowManager.LayoutParams
                    ?: return@setOnTouchListener false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startX = params.x
                        startY = params.y
                        dragging = false

                        view.animate()
                            .scaleX(1.08f)
                            .scaleY(1.08f)
                            .alpha(0.94f)
                            .setDuration(120L)
                            .start()

                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY

                        if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            dragging = true
                        }

                        if (dragging) {
                            val bounds = floatingBounds()
                            bubbleX = (startX + dx.toInt()).coerceIn(0, bounds.first)
                            bubbleY = (startY + dy.toInt()).coerceIn(0, bounds.second)
                            params.x = bubbleX
                            params.y = bubbleY
                            runCatching { windowManager.updateViewLayout(view, params) }
                        }

                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(140L)
                            .start()

                        if (dragging) {
                            snapBubbleToEdge(view, params)
                        } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                            expandPanel()
                        }

                        true
                    }

                    else -> false
                }
            }
        }
    }

    private fun createGlowRingView(): View {
        return object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.dp.toFloat()
            }

            private var phase = 0f

            private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2200L
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    phase = it.animatedFraction
                    invalidate()
                }
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                animator.start()
            }

            override fun onDetachedFromWindow() {
                animator.cancel()
                super.onDetachedFromWindow()
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                val centerX = width / 2f
                val centerY = height / 2f
                val radius = width / 2f - 3.dp

                paint.shader = SweepGradient(
                    centerX,
                    centerY,
                    intArrayOf(
                        Color.rgb(0, 229, 255),
                        Color.rgb(20, 120, 255),
                        Color.rgb(47, 199, 255),
                        Color.rgb(106, 107, 255),
                        Color.rgb(0, 229, 255)
                    ),
                    null
                )

                canvas.save()
                canvas.rotate(phase * 360f, centerX, centerY)
                canvas.drawCircle(centerX, centerY, radius, paint)
                canvas.restore()
            }
        }
    }

    private fun premiumOutlineBackground(oval: Boolean = false): GradientDrawable {
        return GradientDrawable().apply {
            if (oval) shape = GradientDrawable.OVAL else cornerRadius = 32.dp.toFloat()
            setColor(Color.argb(115, 4, 10, 22))
            setStroke(2.dp, Color.argb(118, 47, 199, 255))
        }
    }

    private fun floatingBounds(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        val maxX = (metrics.widthPixels - 76.dp).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - 108.dp).coerceAtLeast(0)
        return maxX to maxY
    }

    private fun snapBubbleToEdge(view: View, params: WindowManager.LayoutParams) {
        val (maxX, maxY) = floatingBounds()
        val targetX = if (params.x < maxX / 2) 16.dp else (maxX - 16.dp).coerceAtLeast(0)
        val targetY = params.y.coerceIn(32.dp, (maxY - 32.dp).coerceAtLeast(32.dp))
        val startX = params.x
        val startY = params.y

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300L
            interpolator = DecelerateInterpolator()

            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                bubbleX = (startX + ((targetX - startX) * t)).toInt()
                bubbleY = (startY + ((targetY - startY) * t)).toInt()
                params.x = bubbleX
                params.y = bubbleY
                runCatching { windowManager.updateViewLayout(view, params) }
            }

            start()
        }
    }

    private fun buildPanelOverlay(): View {
        return FrameLayout(context).apply {
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            setOnClickListener { collapsePanel() }

            addView(
                buildPanel(),
                FrameLayout.LayoutParams(
                    context.resources.displayMetrics.widthPixels.coerceAtMost(430.dp),
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                ).apply {
                    marginStart = 14.dp
                    marginEnd = 14.dp
                    topMargin = 72.dp
                }
            )
        }
    }

    private fun buildPanel(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 18.dp, 16.dp, 16.dp)
            elevation = 32.dp.toFloat()
            background = premiumPanelBackground()
            setOnClickListener { }

            addView(panelHeader())
            addView(askBar())
            addView(actionStrip())

            val suggestions = currentCalendarSuggestions()
            if (suggestions.isNotEmpty()) {
                addView(sectionLabel("SUGGESTIONS"))
                suggestions.forEach { detection ->
                    detection.eventData?.let { event ->
                        addView(suggestionCard(detection, event))
                    }
                }
            }

            addView(sectionLabel("MONITORED"))
            addView(monitoredAppsRow())
            addView(statusRow())
        }
    }

    private fun rebuildPanel() {
        removeCurrentView()
        showPanelNow()
    }

    private fun panelHeader(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 14.dp)

            addView(
                sparkleIcon(),
                LinearLayout.LayoutParams(44.dp, 44.dp).apply {
                    marginEnd = 12.dp
                }
            )

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(singleLineText("iKoo Assist", 19f, Color.WHITE, Typeface.DEFAULT_BOLD))
                    addView(singleLineText("On-device AI - zero cloud", 12f, Color.argb(200, 200, 220, 255), Typeface.DEFAULT))
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            addView(
                TextView(context).apply {
                    text = "×"
                    gravity = Gravity.CENTER
                    textSize = 22f
                    setTextColor(Color.argb(176, 255, 255, 255))
                    setOnClickListener { collapsePanel() }
                },
                LinearLayout.LayoutParams(42.dp, 42.dp)
            )
        }
    }

    private fun sparkleIcon(): View {
        return TextView(context).apply {
            text = "iQ"
            gravity = Gravity.CENTER
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = centerIconBackground()
        }
    }

    private fun askBar(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp, 10.dp, 12.dp, 10.dp)
            background = cardBackground()

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL

                    val input = EditText(context).apply {
                        hint = "Ask iKoo: dinner 10:30, meeting tomorrow..."
                        textSize = 14f
                        isSingleLine = true
                        setTextColor(Color.WHITE)
                        setHintTextColor(Color.rgb(100, 120, 160))
                        setPadding(14.dp, 0, 14.dp, 0)
                        background = inputBackground()
                    }

                    addView(
                        input,
                        LinearLayout.LayoutParams(0, 42.dp, 1f).apply {
                            marginEnd = 10.dp
                        }
                    )

                    addView(
                        TextView(context).apply {
                            text = "ASK"
                            gravity = Gravity.CENTER
                            textSize = 13f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(Color.WHITE)
                            background = blueButtonBackground()
                            setOnClickListener {
                                val prompt = input.text?.toString().orEmpty().trim()
                                if (prompt.isNotBlank()) {
                                    hideKeyboard(input)
                                    runAskIKoo(prompt)
                                }
                            }
                        },
                        LinearLayout.LayoutParams(62.dp, 42.dp)
                    )
                }
            )

            addView(
                singleLineText(askStatus, 12f, Color.rgb(140, 160, 200), Typeface.DEFAULT).apply {
                    setPadding(4.dp, 8.dp, 4.dp, 0)
                }
            )

            setPanelMargins()
        }
    }

    private fun actionStrip(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER

            addView(
                actionChip("SCAN", "screen") {
                    scanning = true
                    askStatus = "Scanning current screen for event times..."
                    removeCurrentView()
                    mainHandler.postDelayed({
                        requestCurrentScreenScan()
                        mainHandler.postDelayed({ showPanelNow() }, PANEL_SCAN_DELAY_MILLIS)
                    }, 180L)
                },
                LinearLayout.LayoutParams(0, 52.dp, 1f).apply { marginEnd = 8.dp }
            )

            addView(
                actionChip("VOICE", "speak") {
                    collapsePanel()
                    launchVoiceOverlay()
                },
                LinearLayout.LayoutParams(0, 52.dp, 1f).apply { marginEnd = 8.dp }
            )

            addView(
                actionChip("SHOT", "search") {
                    if (!isAccessibilityServiceEnabled()) {
                        askStatus = "Enable iKoo Accessibility first to capture screenshots."
                        launchAccessibilitySettings()
                        return@actionChip
                    }

                    askStatus = "Taking screenshot for product search..."

                    // Hide iKoo temporarily so it does not appear in screenshot
                    removeCurrentView()

                    mainHandler.postDelayed({
                        requestProductScreenshotSearch()
                    }, 350L)

                    // Bring floating bubble back after screenshot is triggered
                    mainHandler.postDelayed({
                        show()
                    }, 2200L)
                },
                LinearLayout.LayoutParams(0, 52.dp, 1f).apply { marginEnd = 8.dp }
            )

            addView(
                actionChip("APP", "open") {
                    collapsePanel()
                    launchMainApp()
                },
                LinearLayout.LayoutParams(0, 52.dp, 1f)
            )

            setPanelMargins()
        }
    }

    private fun actionChip(title: String, subtitle: String, onClick: () -> Unit): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(10.dp, 7.dp, 10.dp, 7.dp)
            background = chipBackground()

            addView(singleLineText(title, 13f, Color.WHITE, Typeface.DEFAULT_BOLD))
            addView(singleLineText(subtitle, 10f, Color.rgb(126, 154, 190), Typeface.DEFAULT))

            setOnClickListener { onClick() }
        }
    }

    private fun runAskIKoo(prompt: String) {
        askStatus = "Thinking offline with Gemma..."
        val detection = IntentDetectionStore.update("assistive.ask", prompt)
        rebuildPanel()

        assistantScope.launch {
            AIEngineRegistry.initialize(context.applicationContext)

            val engine = AIEngineRegistry.active()
            val aiResponse = if (engine?.isReady() == true) {
                engine.generate(
                    """
                    You are iKoo, an offline assistant. Extract only useful calendar/reminder/task intent from this user text.
                    If it contains an event, mention the title and time. If not, answer briefly.
                    Text: $prompt
                    """.trimIndent(),
                    maxTokens = 96
                )?.trim()
            } else {
                null
            }

            askStatus = detection.eventData?.let { event ->
                "Detected: ${event.title} ${event.whenLine()} - review suggestion below."
            } ?: aiResponse?.take(96)?.ifBlank { null }
                    ?: "No calendar time found. Try: meeting tomorrow 3 PM."

            rebuildPanel()
        }
    }

    private fun sectionLabel(label: String): View {
        return TextView(context).apply {
            text = label
            textSize = 12f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(122, 148, 186))
            includeFontPadding = false
            setPadding(6.dp, 2.dp, 0, 10.dp)
        }
    }

    private fun quickActionCard(
        title: String,
        subtitle: String,
        mark: String,
        colorHex: String,
        onClick: () -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
            background = cardBackground()

            addView(
                appIcon(colorHex, mark),
                LinearLayout.LayoutParams(48.dp, 48.dp).apply {
                    marginEnd = 14.dp
                }
            )

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(singleLineText(title, 16f, Color.WHITE, Typeface.DEFAULT_BOLD))
                    addView(singleLineText(subtitle, 13f, Color.rgb(178, 184, 196), Typeface.DEFAULT))
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            addView(
                singleLineText("›", 26f, Color.rgb(47, 199, 255), Typeface.DEFAULT_BOLD),
                LinearLayout.LayoutParams(24.dp, 32.dp)
            )

            setOnClickListener { onClick() }
            setPanelMargins()
        }
    }

    private fun monitoredAppCard(app: MonitoredApp): View {
        val installed = app.packages.any { isPackageInstalled(it) }
        val recent = latestDetectionFor(app.packages)

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
            background = cardBackground()

            addView(
                appIcon(app.colorHex, app.mark),
                LinearLayout.LayoutParams(48.dp, 48.dp).apply {
                    marginEnd = 14.dp
                }
            )

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(singleLineText(app.name, 16f, Color.WHITE, Typeface.DEFAULT_BOLD))
                    addView(
                        singleLineText(
                            recent?.eventData?.let { event ->
                                "Found: ${event.title} ${event.whenLine()}"
                            } ?: if (installed) {
                                "Listening for meeting, dinner, calls, and reminders"
                            } else {
                                "Not installed on this device"
                            },
                            13f,
                            Color.rgb(178, 184, 196),
                            Typeface.DEFAULT
                        )
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            addView(
                singleLineText(
                    if (recent?.eventData != null) "Ready" else if (installed) "On" else "Off",
                    13f,
                    if (installed) Color.rgb(47, 199, 255) else Color.rgb(154, 154, 154),
                    Typeface.DEFAULT_BOLD
                )
            )

            setOnClickListener {
                val launchPackage = app.packages.firstOrNull { isPackageInstalled(it) }
                launchPackage?.let {
                    context.packageManager.getLaunchIntentForPackage(it)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let(context::startActivity)
                }
            }

            setPanelMargins()
        }
    }

    private fun monitoredAppsRow(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER

            monitoredApps().take(4).forEachIndexed { index, app ->
                addView(
                    monitoredAppChip(app),
                    LinearLayout.LayoutParams(0, 58.dp, 1f).apply {
                        if (index < 3) marginEnd = 8.dp
                    }
                )
            }

            setPanelMargins()
        }
    }

    private fun monitoredAppChip(app: MonitoredApp): View {
        val installed = app.packages.any { isPackageInstalled(it) }
        val ready = latestDetectionFor(app.packages)?.eventData != null

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(6.dp, 6.dp, 6.dp, 6.dp)

            background = GradientDrawable().apply {
                cornerRadius = 17.dp.toFloat()
                setColor(Color.rgb(8, 18, 38))
                setStroke(
                    1.dp,
                    if (installed) Color.argb(100, 47, 199, 255) else Color.argb(42, 120, 140, 170)
                )
            }

            addView(
                singleLineText(app.mark, 13f, Color.WHITE, Typeface.DEFAULT_BOLD).apply {
                    gravity = Gravity.CENTER
                }
            )

            addView(
                singleLineText(
                    if (ready) "ready" else if (installed) "on" else "off",
                    10f,
                    if (installed) Color.rgb(47, 199, 255) else Color.rgb(126, 138, 156),
                    Typeface.DEFAULT_BOLD
                ).apply {
                    gravity = Gravity.CENTER
                }
            )

            setOnClickListener {
                val launchPackage = app.packages.firstOrNull { isPackageInstalled(it) }
                launchPackage?.let {
                    context.packageManager.getLaunchIntentForPackage(it)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let(context::startActivity)
                }
            }
        }
    }

    private fun suggestionCard(detection: LatestIntentDetection, eventData: EventData): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
            background = cardBackground()

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL

                    addView(
                        appIcon(sourceColor(detection.packageName), sourceMark(detection.packageName)),
                        LinearLayout.LayoutParams(42.dp, 42.dp).apply {
                            marginEnd = 12.dp
                        }
                    )

                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(singleLineText(sourceName(detection.packageName), 12f, Color.rgb(47, 199, 255), Typeface.DEFAULT_BOLD))
                            addView(singleLineText(eventData.cleanTitle(), 16f, Color.WHITE, Typeface.DEFAULT_BOLD))
                            addView(singleLineText(eventData.whenLine(), 13f, Color.rgb(178, 184, 196), Typeface.DEFAULT))
                        },
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    )
                }
            )

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    setPadding(0, 10.dp, 0, 0)

                    addView(
                        calendarDecisionButton("Reject", filled = false) {
                            dismissedSuggestionKeys += detection.suggestionKey()
                            HistoryRepository.getInstance(context).markLatestActionTaken("Dismissed")
                            rebuildPanel()
                        }
                    )

                    addView(
                        calendarDecisionButton("Accept", filled = true) {
                            collapsePanel()
                            CalendarActionManager.openCalendarInsert(context, eventData)
                        }
                    )
                }
            )

            setPanelMargins()
        }
    }

    private fun emptySuggestionCard(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 18.dp, 20.dp, 18.dp)
            background = cardBackground()

            addView(
                singleLineText("No event suggestions yet", 15f, Color.WHITE, Typeface.DEFAULT_BOLD).apply {
                    gravity = Gravity.CENTER
                }
            )

            addView(
                singleLineText("Tap SCAN or ask: dinner tomorrow at 8 PM.", 12f, Color.rgb(140, 160, 190), Typeface.DEFAULT).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, 8.dp, 0, 0)
                }
            )

            setPanelMargins()
        }
    }

    private fun calendarDecisionButton(label: String, filled: Boolean, onClick: () -> Unit): View {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (filled) Color.WHITE else Color.rgb(244, 247, 255))
            setPadding(16.dp, 10.dp, 16.dp, 10.dp)
            background = if (filled) blueButtonBackground() else secondaryButtonBackground()
            setOnClickListener { onClick() }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 10.dp
            }
        }
    }

    private fun currentCalendarSuggestions(): List<LatestIntentDetection> {
        val latest = IntentDetectionStore.latestDetection.value

        return (listOf(latest) + IntentDetectionStore.detectionHistory.value)
            .filter { it.result.type == IntentType.CALENDAR_EVENT && it.eventData != null }
            .filter { it.isRealCalendarSuggestion() }
            .filterNot { it.isSelfOverlayDetection() }
            .filterNot { it.suggestionKey() in dismissedSuggestionKeys }
            .distinctBy { "${it.packageName}|${it.eventData?.title}|${it.eventData?.datePhrase}|${it.eventData?.timePhrase}" }
            .take(3)
    }

    private fun latestDetectionFor(packages: List<String>): LatestIntentDetection? {
        return (listOf(IntentDetectionStore.latestDetection.value) + IntentDetectionStore.detectionHistory.value)
            .firstOrNull { detection ->
                packages.any { pkg -> detection.packageName.equals(pkg, ignoreCase = true) } &&
                        detection.result.type == IntentType.CALENDAR_EVENT &&
                        detection.eventData != null &&
                        detection.isRealCalendarSuggestion() &&
                        !detection.isSelfOverlayDetection() &&
                        detection.suggestionKey() !in dismissedSuggestionKeys
            }
    }

    private fun LatestIntentDetection.isRealCalendarSuggestion(): Boolean {
        val event = eventData ?: return false
        val title = event.cleanTitle()
        val visible = visibleText.lowercase()

        if (!event.isRealCalendarSuggestion()) return false
        if (visible.isUiNoise()) return false
        if (visible.isFinancialNoise()) return false

        val strongEventText = listOf(title, visibleText).joinToString(" ").lowercase()
        val hasEventWord = EVENT_WORDS.any { it in strongEventText }

        val fromTrustedNotification = packageName.contains("whatsapp", true) ||
                packageName.contains("gm", true) ||
                packageName.contains("messaging", true) ||
                packageName.contains("teams", true) ||
                packageName.contains("telegram", true) ||
                packageName.contains("slack", true) ||
                packageName == "assistive.ask" ||
                packageName == "voice"

        return hasEventWord && fromTrustedNotification
    }

    private fun EventData.isRealCalendarSuggestion(): Boolean {
        val title = cleanTitle()

        if (title.length < 3) return false
        if (timePhrase.isNullOrBlank()) return false
        if (title.isUiNoise()) return false
        if (title.isFinancialNoise()) return false

        return true
    }

    private fun EventData.cleanTitle(): String {
        return title
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', '|')
            .take(64)
    }

    private fun String.isUiNoise(): Boolean {
        val text = lowercase()

        return listOf(
            "search in mail",
            "open navigation drawer",
            "signed in as",
            "google one",
            "show calendar list",
            "settings drawer",
            "jump to today",
            "add birthdays",
            "be reminded",
            "social ",
            "gmail",
            "inbox",
            "show contact information",
            "add emoji reaction",
            "smart reply",
            "tap to edit",
            "more options",
            "reply more",
            "to me"
        ).any { it in text }
    }

    private fun String.isFinancialNoise(): Boolean {
        val text = lowercase()

        return listOf(
            "account number",
            "transaction info",
            "upi",
            "debited",
            "credited",
            "bank",
            "balance",
            "compose mail",
            "more than 99 new notifications"
        ).any { it in text }
    }

    private fun LatestIntentDetection.isSelfOverlayDetection(): Boolean {
        if (packageName == "assistive.ask") return false

        val text = visibleText.lowercase()
        val title = eventData?.title.orEmpty().lowercase()

        return listOf(
            "ikoo assist",
            "ask ikoo",
            "quick actions",
            "on-device ai",
            "zero cloud",
            "gallery search",
            "smart insights",
            "monitored apps"
        ).any { marker -> marker in text || marker in title }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        val packageManager = context.packageManager

        return runCatching {
            packageManager.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false) || runCatching {
            packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    private fun requestCurrentScreenScan() {
        context.sendBroadcast(
            Intent(IKooAccessibilityService.ACTION_SCAN_CURRENT_SCREEN)
                .setPackage(context.packageName)
        )
    }

    private fun requestProductScreenshotSearch() {
        context.sendBroadcast(
            Intent(IKooAccessibilityService.ACTION_PRODUCT_SCREENSHOT_SEARCH)
                .setPackage(context.packageName)
        )
    }

    private fun launchAccessibilitySettings() {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = ComponentName(context, IKooAccessibilityService::class.java)

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        return enabledServices
            .split(':')
            .any { ComponentName.unflattenFromString(it) == expectedService }
    }

    private data class MonitoredApp(
        val name: String,
        val mark: String,
        val colorHex: String,
        val packages: List<String>
    )

    private fun monitoredApps(): List<MonitoredApp> = listOf(
        MonitoredApp("WhatsApp", "WA", "#25D366", listOf("com.whatsapp", "com.whatsapp.w4b")),
        MonitoredApp("Gmail", "GM", "#FFFFFF", listOf("com.google.android.gm")),
        MonitoredApp("Messages", "MS", "#24A8FF", listOf("com.google.android.apps.messaging")),
        MonitoredApp("Teams", "TM", "#6D5CFF", listOf("com.microsoft.teams")),
        MonitoredApp("Telegram", "TG", "#24A8FF", listOf("org.telegram.messenger")),
        MonitoredApp("Slack", "SL", "#1478FF", listOf("com.slack"))
    )

    private fun LatestIntentDetection.suggestionKey(): String {
        return "${packageName}|${eventData?.title}|${eventData?.datePhrase}|${eventData?.timePhrase}|${visibleText.hashCode()}"
    }

    private fun EventData.whenLine(): String {
        return listOfNotNull(datePhrase, timePhrase, location?.let { "at $it" })
            .joinToString(" · ")
            .ifBlank { "Time detected" }
    }

    private fun sourceName(packageName: String): String {
        return when {
            packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
            packageName.contains("gm", ignoreCase = true) -> "Gmail"
            packageName.contains("messaging", ignoreCase = true) -> "Messages"
            packageName.contains("teams", ignoreCase = true) -> "Teams"
            packageName.contains("telegram", ignoreCase = true) -> "Telegram"
            packageName.contains("slack", ignoreCase = true) -> "Slack"
            packageName == "voice" -> "Voice"
            else -> "Screen"
        }
    }

    private fun sourceMark(packageName: String): String {
        return when {
            packageName.contains("whatsapp", ignoreCase = true) -> "WA"
            packageName.contains("gm", ignoreCase = true) -> "GM"
            packageName.contains("messaging", ignoreCase = true) -> "MS"
            packageName.contains("teams", ignoreCase = true) -> "TM"
            packageName.contains("telegram", ignoreCase = true) -> "TG"
            packageName.contains("slack", ignoreCase = true) -> "SL"
            packageName == "voice" -> "VC"
            else -> "AI"
        }
    }

    private fun sourceColor(packageName: String): String {
        return when {
            packageName.contains("whatsapp", ignoreCase = true) -> "#25D366"
            packageName.contains("gm", ignoreCase = true) -> "#FFFFFF"
            packageName.contains("messaging", ignoreCase = true) -> "#24A8FF"
            packageName.contains("teams", ignoreCase = true) -> "#6D5CFF"
            packageName.contains("telegram", ignoreCase = true) -> "#24A8FF"
            packageName.contains("slack", ignoreCase = true) -> "#1478FF"
            else -> "#2FC7FF"
        }
    }

    private fun statusRow(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 13.dp, 16.dp, 13.dp)
            background = pillBackground()

            addView(
                singleLineText("Hey iKoo", 14f, Color.WHITE, Typeface.DEFAULT_BOLD),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            addView(singleLineText("ACTIVE", 11f, Color.rgb(47, 199, 255), Typeface.DEFAULT_BOLD))
        }
    }

    private fun appIcon(colorHex: String, mark: String): View {
        return TextView(context).apply {
            text = mark
            gravity = Gravity.CENTER
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (colorHex == "#FFFFFF") Color.rgb(234, 67, 53) else Color.WHITE)
            isSingleLine = true
            background = GradientDrawable().apply {
                cornerRadius = 12.dp.toFloat()
                setColor(Color.parseColor(colorHex))
            }
        }
    }

    private fun singleLineText(textValue: String, size: Float, color: Int, face: Typeface): TextView {
        return TextView(context).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            typeface = face
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = true
        }
    }

    private fun blueButtonBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(20, 120, 255), Color.rgb(47, 199, 255))
        ).apply {
            cornerRadius = 24.dp.toFloat()
        }
    }

    private fun secondaryButtonBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 22.dp.toFloat()
            setColor(Color.argb(30, 255, 255, 255))
            setStroke(1.dp, Color.argb(80, 255, 255, 255))
        }
    }

    private fun hideKeyboard(view: View) {
        val inputManager = context.getSystemService(InputMethodManager::class.java)
        inputManager?.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun LinearLayout.setPanelMargins() {
        setLayoutParams(
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dp
            }
        )
    }

    private fun cardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 20.dp.toFloat()
            setColor(Color.rgb(8, 18, 38))
            setStroke(1.dp, Color.argb(82, 47, 199, 255))
        }
    }

    private fun chipBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 20.dp.toFloat()
            setColor(Color.rgb(8, 18, 38))
            setStroke(1.dp, Color.argb(96, 47, 199, 255))
        }
    }

    private fun inputBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 24.dp.toFloat()
            setColor(Color.rgb(6, 12, 28))
            setStroke(1.dp, Color.argb(64, 47, 199, 255))
        }
    }

    private fun pillBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 999.dp.toFloat()
            setColor(Color.argb(42, 47, 199, 255))
            setStroke(1.dp, Color.argb(64, 47, 199, 255))
        }
    }

    private fun premiumPanelBackground(): GradientDrawable {
        return GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.TL_BR
            colors = intArrayOf(
                Color.argb(252, 10, 20, 45),
                Color.argb(250, 6, 10, 22),
                Color.argb(252, 5, 14, 35)
            )
            cornerRadius = 32.dp.toFloat()
            setStroke(2.dp, Color.argb(124, 47, 199, 255))
        }
    }

    private fun centerIconBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(20, 120, 255))
        }
    }

    private fun iqooOrbBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(47, 199, 255),
                Color.rgb(20, 120, 255),
                Color.rgb(106, 107, 255),
                Color.rgb(9, 11, 17)
            )
        ).apply {
            shape = GradientDrawable.OVAL
        }
    }

    private fun floatingLayoutParams(): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics

        if (bubbleX < 0) bubbleX = (metrics.widthPixels - 82.dp).coerceAtLeast(0)
        if (bubbleY < 0) bubbleY = (metrics.heightPixels / 2 - 35.dp).coerceAtLeast(32.dp)

        return WindowManager.LayoutParams(
            70.dp,
            70.dp,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX
            y = bubbleY
        }
    }

    private fun panelLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    private fun registerWakeWordReceiver() {
        if (wakeWordReceiverRegistered) return

        ContextCompat.registerReceiver(
            context,
            wakeWordReceiver,
            android.content.IntentFilter(HeyIKooService.ACTION_WAKE_WORD_DETECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        wakeWordReceiverRegistered = true
    }

    private fun unregisterWakeWordReceiver() {
        if (!wakeWordReceiverRegistered) return
        runCatching { context.unregisterReceiver(wakeWordReceiver) }
        wakeWordReceiverRegistered = false
    }

    private companion object {
        const val PANEL_SCAN_DELAY_MILLIS = 420L

        val EVENT_WORDS = listOf(
            "meeting",
            "meet",
            "dinner",
            "lunch",
            "breakfast",
            "call",
            "appointment",
            "interview",
            "exam",
            "class",
            "party",
            "reservation",
            "session",
            "visit"
        )
    }
}

private class EdgeGlowView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        maskFilter = BlurMaskFilter(
            18f * context.resources.displayMetrics.density,
            BlurMaskFilter.Blur.NORMAL
        )
    }

    private val rect = RectF()
    private val path = Path()
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val inset = 8f * density
        val radius = 50f * density

        rect.set(inset, inset, width - inset, height - inset)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)

        val shader = LinearGradient(
            0f,
            height.toFloat(),
            width.toFloat(),
            0f,
            intArrayOf(
                Color.rgb(0, 229, 255),
                Color.rgb(20, 120, 255),
                Color.rgb(47, 199, 255),
                Color.rgb(106, 107, 255)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        val fade = 1f - kotlin.math.abs(phase - 0.5f) * 2f

        bloomPaint.shader = shader
        bloomPaint.strokeWidth = 22f * density
        bloomPaint.alpha = (58 + fade * 64).toInt()
        bloomPaint.pathEffect = DashPathEffect(
            floatArrayOf(width * 0.42f, width * 0.18f),
            -phase * width * 2.1f
        )
        canvas.drawPath(path, bloomPaint)

        paint.shader = shader
        paint.strokeWidth = 7f * density
        paint.alpha = (126 + fade * 90).toInt()
        paint.pathEffect = DashPathEffect(
            floatArrayOf(width * 0.36f, width * 0.25f),
            -phase * width * 2.1f
        )
        canvas.drawPath(path, paint)

        paint.shader = null
        paint.color = Color.WHITE
        paint.strokeWidth = 1.4f * density
        paint.alpha = 160
        paint.pathEffect = DashPathEffect(
            floatArrayOf(width * 0.14f, width * 0.74f),
            -phase * width * 2.4f
        )
        canvas.drawPath(path, paint)
    }
}
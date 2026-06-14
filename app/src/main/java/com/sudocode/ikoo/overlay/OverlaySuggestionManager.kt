package com.sudocode.ikoo.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.sudocode.ikoo.calendar.CalendarActionManager
import com.sudocode.ikoo.intent.EventData

class OverlaySuggestionManager(
    private val context: Context
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var lastShownKey: String = ""
    private var lastShownAtMillis: Long = 0L

    fun showCalendarSuggestion(eventData: EventData, originalText: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showCalendarSuggestion(eventData, originalText) }
            return
        }
        if (!Settings.canDrawOverlays(context)) return

        val now = System.currentTimeMillis()
        val key = "${eventData.title}|${eventData.datePhrase}|${eventData.timePhrase}|${eventData.location}"
        if (key == lastShownKey && now - lastShownAtMillis < SAME_EVENT_SUPPRESSION_MILLIS) return

        lastShownKey = key
        lastShownAtMillis = now
        hide(immediate = true)

        val card = buildOverlayCard(eventData = eventData, originalText = originalText)
        overlayView = card
        windowManager.addView(card, overlayLayoutParams())

        card.alpha = 0f
        card.translationY = -36f
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(260L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun hide(immediate: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide(immediate) }
            return
        }
        val view = overlayView ?: return
        overlayView = null

        if (immediate) {
            runCatching { windowManager.removeView(view) }
            return
        }

        view.animate()
            .alpha(0f)
            .translationY(-28f)
            .setDuration(180L)
            .withEndAction {
                runCatching { windowManager.removeView(view) }
            }
            .start()
    }

    private fun buildOverlayCard(eventData: EventData, originalText: String): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp, 18.dp, 22.dp, 18.dp)
            elevation = 22.dp.toFloat()
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.argb(238, 32, 36, 50),
                    Color.argb(224, 16, 20, 30)
                )
            ).apply {
                cornerRadius = 28.dp.toFloat()
                setStroke(1.dp, Color.argb(70, 255, 255, 255))
            }
        }

        val title = TextView(context).apply {
            text = "Event detected"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val message = TextView(context).apply {
            text = originalText.ifBlank { eventData.overlaySummary() }
            setTextColor(Color.argb(222, 234, 240, 255))
            textSize = 14f
            maxLines = 2
            setPadding(0, 7.dp, 0, 14.dp)
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        actions.addView(
            actionButton(
                label = "Add to Calendar",
                filled = true,
                onClick = {
                    hide(immediate = false)
                    CalendarActionManager.openCalendarInsert(context, eventData)
                }
            )
        )
        actions.addView(
            actionButton(
                label = "Dismiss",
                filled = false,
                onClick = { hide(immediate = false) }
            )
        )

        card.addView(title)
        card.addView(message)
        card.addView(actions)
        return card
    }

    private fun actionButton(
        label: String,
        filled: Boolean,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (filled) Color.rgb(8, 10, 16) else Color.argb(230, 234, 240, 255))
            setPadding(14.dp, 10.dp, 14.dp, 10.dp)
            background = GradientDrawable().apply {
                cornerRadius = 18.dp.toFloat()
                if (filled) {
                    setColor(Color.rgb(108, 244, 196))
                } else {
                    setColor(Color.argb(28, 255, 255, 255))
                    setStroke(1.dp, Color.argb(48, 255, 255, 255))
                }
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 10.dp
            }
        }
    }

    private fun overlayLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 28.dp
            horizontalMargin = 0.045f
        }
    }

    private fun EventData.overlaySummary(): String {
        return listOfNotNull(title, datePhrase, timePhrase, location?.let { "at $it" })
            .joinToString(" ")
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val SAME_EVENT_SUPPRESSION_MILLIS = 30_000L
    }
}

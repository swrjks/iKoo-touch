package com.sudocode.ikoo.usage

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.sudocode.ikoo.accessibility.IKooAccessibilityService

class UsageLimitWarningActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty().ifBlank { "This app" }
        val minutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 0)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36.dp, 32.dp, 36.dp, 32.dp)
            setBackgroundColor(0xFF06111F.toInt())
        }
        root.addView(TextView(this).apply {
            text = "Usage limit reached"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "$appName crossed ${minutes}m today."
            textSize = 16f
            setTextColor(0xFFB9D7FF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 14.dp, 0, 24.dp)
        })
        root.addView(TextView(this).apply {
            text = "Exit app"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF168BFF.toInt())
            setPadding(28.dp, 14.dp, 28.dp, 14.dp)
            setOnClickListener {
                sendBroadcast(android.content.Intent(IKooAccessibilityService.ACTION_EXIT_LIMITED_APP).setPackage(packageName))
                finish()
            }
        })
        root.addView(TextView(this).apply {
            text = "Close warning"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF8DBAFF.toInt())
            setPadding(0, 18.dp, 0, 0)
            setOnClickListener { finish() }
        })
        setContentView(root)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_LIMIT_MINUTES = "limit_minutes"
    }
}

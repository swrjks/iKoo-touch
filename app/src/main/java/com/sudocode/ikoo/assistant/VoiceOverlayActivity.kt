package com.sudocode.ikoo.assistant

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.sudocode.ikoo.ui.assistant.VoiceAssistantOverlay
import com.sudocode.ikoo.ui.theme.IKooTheme
import com.sudocode.ikoo.wakeword.HeyIKooService

/**
 * Transparent fullscreen activity hosting the "Hey iKoo" voice assistant
 * overlay (see [VoiceAssistantOverlay]). Launched from
 * HeyIKooService when wake word is detected.
 */
class VoiceOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pause wakeword service to release microphone immediately
        ContextCompat.startForegroundService(
            this,
            Intent(this, HeyIKooService::class.java).apply {
                action = HeyIKooService.ACTION_PAUSE_WAKEWORD
            }
        )

        enableEdgeToEdge()

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)  // ADDED: Removes dim effect for proper glowing assistant overlay

        // No delay needed - VoiceAssistantOverlay will handle mic initialization
        setContent {
            IKooTheme {
                VoiceAssistantOverlay(
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        val appContext = applicationContext

        // FIX 3: Increased from 1500L to 3500L to ensure overlay mic is fully released
        Handler(Looper.getMainLooper()).postDelayed({
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, HeyIKooService::class.java).apply {
                    action = HeyIKooService.ACTION_RESUME_WAKEWORD
                }
            )
        }, 3500L)  // Changed from 1500L to 3500L

        super.onDestroy()
    }
}
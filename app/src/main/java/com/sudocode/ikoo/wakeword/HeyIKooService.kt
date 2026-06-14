package com.sudocode.ikoo.wakeword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.sudocode.ikoo.R
import com.sudocode.ikoo.assistant.VoiceOverlayActivity

class HeyIKooService : Service() {
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldRun = true
    private var restartDelay = NORMAL_RESTART_DELAY
    private var lastWakeLaunchAt = 0L

    private val restartRunnable = Runnable {
        if (shouldRun && !isListening) startListening()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        // REMOVED: startListening() - now only starts via onStartCommand or resume
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_WAKEWORD -> {
                Log.i(TAG, "Wakeword paused")
                shouldRun = false
                handler.removeCallbacks(restartRunnable)
                releaseRecognizer()
            }

            ACTION_RESUME_WAKEWORD -> {
                Log.i(TAG, "Wakeword resumed")
                shouldRun = true
                restartDelay = NORMAL_RESTART_DELAY
                handler.removeCallbacks(restartRunnable)
                releaseRecognizer()
                scheduleRestart(2500L)
            }

            else -> {
                shouldRun = true
                if (!isListening) scheduleRestart(400L)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shouldRun = false
        handler.removeCallbacks(restartRunnable)
        releaseRecognizer()
        super.onDestroy()
    }

    private fun startListening() {
        if (isListening || !SpeechRecognizer.isRecognitionAvailable(this)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Microphone permission missing; wake word listener paused")
            scheduleRestart(10_000L)
            return
        }

        releaseRecognizer()
        isListening = true

        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.lowercase()
                        .orEmpty()
                    if (isWakePhrase(partial)) {
                        Log.i(TAG, "Wake word detected from partial: $partial")
                        launchVoiceOverlay()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val finalText = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()

                    if (isWakePhrase(finalText)) {
                        Log.i(TAG, "Wake word detected from final result: $finalText")
                        launchVoiceOverlay()
                        return
                    }

                    isListening = false
                    releaseRecognizer()
                    scheduleRestart(NORMAL_RESTART_DELAY)
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "Wakeword recognizer error: $error")

                    isListening = false
                    releaseRecognizer()

                    val delay = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> NORMAL_RESTART_DELAY

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 6500L
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 15_000L
                        SpeechRecognizer.ERROR_CLIENT -> 8000L

                        else -> restartDelay.coerceAtMost(MAX_RESTART_DELAY)
                    }

                    restartDelay = (delay + 1500L).coerceAtMost(MAX_RESTART_DELAY)
                    scheduleRestart(delay)
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }

        runCatching {
            recognizer?.startListening(recognizerIntent)
        }.onFailure {
            Log.e(TAG, "Could not start wake word listener", it)
            isListening = false
            releaseRecognizer()
            scheduleRestart(2_000L)
        }
    }

    private fun isWakePhrase(text: String): Boolean {
        val clean = text
            .lowercase()
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()

        return WAKE_PHRASES.any { clean.contains(it) }
    }

    private fun launchVoiceOverlay() {
        val now = System.currentTimeMillis()

        // Prevent duplicate launches
        if (now - lastWakeLaunchAt < 3000L) return
        lastWakeLaunchAt = now

        // Stop wake listener before overlay starts its own mic
        shouldRun = false
        handler.removeCallbacks(restartRunnable)
        releaseRecognizer()

        sendBroadcast(Intent(ACTION_WAKE_WORD_DETECTED))

        handler.postDelayed({
            runCatching {
                startActivity(Intent(this, VoiceOverlayActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    putExtra("triggered_by_wake_word", true)
                })
            }.onFailure {
                Log.e(TAG, "Could not launch voice overlay", it)
                shouldRun = true
                scheduleRestart(5000L)
            }
        }, 250L)  // CHANGED: from 1200L to 250L for faster overlay appearance
    }

    private fun scheduleRestart(delayMillis: Long) {
        handler.removeCallbacks(restartRunnable)
        if (shouldRun) {
            handler.postDelayed(restartRunnable, delayMillis)
        }
    }

    private fun releaseRecognizer() {
        isListening = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "iKoo Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "iKoo is listening for your voice"
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("iKoo is active")
            .setContentText("Say \"Hey iKoo\" anytime")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val TAG = "HeyIKoo"
        private const val CHANNEL_ID = "ikoo_wake_channel"
        private const val NOTIF_ID = 1001

        const val ACTION_WAKE_WORD_DETECTED = "com.sudocode.ikoo.WAKE_WORD_DETECTED"
        const val ACTION_PAUSE_WAKEWORD = "com.sudocode.ikoo.PAUSE_WAKEWORD"
        const val ACTION_RESUME_WAKEWORD = "com.sudocode.ikoo.RESUME_WAKEWORD"

        private const val NORMAL_RESTART_DELAY = 2500L
        private const val MAX_RESTART_DELAY = 20_000L

        private val WAKE_PHRASES = listOf(
            "hey ikoo",
            "hi ikoo",
            "hello ikoo",
            "hey iqoo",
            "hi iqoo",
            "hello iqoo",
            "hey iko",
            "hi iko",
            "hey ico",
            "hi ico",
            "hey ikon",
            "hi ikon",
            "ok ikoo",
            "okay ikoo",
            "okay iqoo"
        )
    }
}
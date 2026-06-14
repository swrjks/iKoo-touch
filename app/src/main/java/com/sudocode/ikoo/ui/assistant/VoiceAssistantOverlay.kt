package com.sudocode.ikoo.ui.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudocode.ikoo.intent.IntentDetectionStore
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

enum class AssistantState { WAKING, LISTENING, THINKING, RESPONDING }

@Composable
fun VoiceAssistantOverlay(onClose: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var state by remember { mutableStateOf(AssistantState.WAKING) }
    var transcript by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var displayedText by remember { mutableStateOf("") }
    var audioLevels by remember { mutableStateOf(List(40) { 0f }) }
    var borderProgress by remember { mutableStateOf(0f) }

    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var listenRequest by remember { mutableStateOf(0) }
    var isActuallyListening by remember { mutableStateOf(false) }

    val edgeColors = listOf(
        Color(0xFF00E5FF),
        Color(0xFF1478FF),
        Color(0xFF2FC7FF),
        Color(0xFF6A6BFF)
    )

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            listenRequest++
        } else {
            responseText = "Microphone permission is required for iKoo voice mode."
            state = AssistantState.RESPONDING
        }
    }

    fun stopOverlayRecognizer() {
        isActuallyListening = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    fun requestListen() {
        transcript = ""
        responseText = ""
        displayedText = ""
        state = AssistantState.WAKING
        listenRequest++
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(Unit) {
        requestListen()
    }

    // FIX 4: Changed from LaunchedEffect(state) to LaunchedEffect(Unit)
    // Now the border animation will complete even if state changes
    LaunchedEffect(Unit) {
        borderProgress = 0f
        while (borderProgress < 1f) {
            delay(16)
            borderProgress = (borderProgress + 0.025f).coerceAtMost(1f)
        }
    }

    LaunchedEffect(listenRequest) {
        if (listenRequest == 0) return@LaunchedEffect

        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        if (!granted) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return@LaunchedEffect
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            responseText = "Voice recognition is not available on this phone."
            state = AssistantState.RESPONDING
            return@LaunchedEffect
        }

        stopOverlayRecognizer()

        state = AssistantState.WAKING
        transcript = "Getting ready..."

        // FIX 2: Changed from 350L to 1500L
        delay(1500L)

        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isActuallyListening = true
                    state = AssistantState.LISTENING
                    transcript = "Listening..."
                    responseText = ""
                }

                override fun onBeginningOfSpeech() {
                    isActuallyListening = true
                    state = AssistantState.LISTENING
                    transcript = ""
                    responseText = ""
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()

                    if (text.isNotBlank()) {
                        transcript = text
                        state = AssistantState.LISTENING
                    }
                }

                override fun onResults(results: Bundle?) {
                    isActuallyListening = false

                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                        .trim()

                    if (text.isNotBlank()) {
                        transcript = text
                        state = AssistantState.THINKING
                        responseText = buildResponse(context, text) // Pass context
                        state = AssistantState.RESPONDING
                    } else {
                        responseText = "I did not hear a command. Tap the glowing iKoo button and speak again."
                        state = AssistantState.RESPONDING
                    }
                }

                override fun onError(error: Int) {
                    isActuallyListening = false

                    responseText = when (error) {
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            "I was ready, but I did not hear your command. Tap the glowing iKoo button and speak again."
                        }

                        SpeechRecognizer.ERROR_NO_MATCH -> {
                            "I heard noise, but could not understand words. Tap the iKoo button and say: schedule meeting at 5 PM."
                        }

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            "Mic is busy. Wait one second, then tap the glowing iKoo button."
                        }

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            "Microphone permission is missing. Allow microphone permission and try again."
                        }

                        else -> {
                            "Voice error $error. Tap the glowing iKoo button and try again."
                        }
                    }

                    state = AssistantState.RESPONDING
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    audioLevels = audioLevels.drop(1) + normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        try {
            recognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            responseText = "Could not start microphone. Tap the glowing iKoo button and try again."
            state = AssistantState.RESPONDING
        }
    }

    LaunchedEffect(responseText, state) {
        if (state == AssistantState.RESPONDING && responseText.isNotBlank()) {
            displayedText = ""
            responseText.forEachIndexed { index, char ->
                delay(18)
                displayedText += char
                if (index % 12 == 0) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    LaunchedEffect(state, listenRequest) {
        if (state == AssistantState.LISTENING || state == AssistantState.WAKING) {
            delay(12000L)

            if (state == AssistantState.LISTENING || state == AssistantState.WAKING) {
                stopOverlayRecognizer()
                responseText = "I did not hear a command. Tap the glowing iKoo button and speak again."
                state = AssistantState.RESPONDING
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopOverlayRecognizer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f))
    ) {
        EdgeLightBorder(
            progress = borderProgress,
            colors = edgeColors,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PremiumAssistantOrb(
                state = state,
                audioLevels = audioLevels,
                onClick = {
                    stopOverlayRecognizer()
                    requestListen()
                }
            )

            Spacer(modifier = Modifier.height(48.dp))

            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) +
                            slideInVertically(initialOffsetY = { 40 })) togetherWith
                            (fadeOut() + slideOutVertically(targetOffsetY = { -40 }))
                },
                label = "assistantCard"
            ) { currentState ->
                when (currentState) {
                    AssistantState.WAKING -> WakingCard()
                    AssistantState.LISTENING -> ListeningCard(transcript, audioLevels)
                    AssistantState.THINKING -> ThinkingCard()
                    AssistantState.RESPONDING -> RespondingCard(
                        displayedText.ifBlank { responseText }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tap the glowing iKoo orb to listen again",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Close",
                color = Color(0xFF00E5FF),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onClose() }
            )
        }
    }
}

@Composable
private fun PremiumAssistantOrb(
    state: AssistantState,
    audioLevels: List<Float>,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "rotation"
    )

    val orbSize = when (state) {
        AssistantState.WAKING -> 120.dp
        AssistantState.LISTENING -> 160.dp
        AssistantState.THINKING -> 140.dp
        AssistantState.RESPONDING -> 120.dp
    }
    val scale by animateFloatAsState(
        targetValue = if (state == AssistantState.LISTENING) {
            1f + (audioLevels.lastOrNull() ?: 0f) * 0.15f
        } else {
            1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "voiceScale"
    )
    val orbColors = listOf(
        Color(0xFF00E5FF),
        Color(0xFF1478FF),
        Color(0xFF2FC7FF),
        Color(0xFF6A6BFF),
        Color(0xFF00E5FF)
    )

    Box(modifier = Modifier.size(orbSize).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(orbSize)) {
            val strokeWidth = 4.dp.toPx()
            drawCircle(
                brush = Brush.sweepGradient(orbColors),
                radius = size.minDimension / 2f - strokeWidth,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
        Box(
            modifier = Modifier
                .size(orbSize * (0.9f + glowIntensity * 0.15f))
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(orbSize * 0.5f * scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFF00E5FF).copy(alpha = 0.62f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Fixed: animateFloatAsState should not use infiniteRepeatable
        val breathScale by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breath"
        )
        Box(
            modifier = Modifier
                .size(20.dp * breathScale)
                .clip(CircleShape)
                .background(Color.White)
        )

        if (state == AssistantState.LISTENING) {
            audioLevels.takeLast(3).forEachIndexed { index, level ->
                val ringScale by animateFloatAsState(
                    targetValue = 1f + level * 0.5f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                    label = "ring$index"
                )
                Box(
                    modifier = Modifier
                        .size(orbSize * ringScale)
                        .clip(CircleShape)
                        .border(
                            width = (3 - index).dp,
                            color = orbColors[index].copy(alpha = 0.56f - index * 0.16f),
                            shape = CircleShape
                        )
                )
            }
        }

        if (state == AssistantState.THINKING) {
            ParticleRing(modifier = Modifier.size(orbSize * 1.4f))
        }
    }
}

@Composable
private fun ParticleRing(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val particleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "particleRotation"
    )

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2.2f
        repeat(12) { index ->
            val angle = particleRotation + (index * 360f / 12)
            val x = center.x + radius * cos(Math.toRadians(angle.toDouble())).toFloat()
            val y = center.y + radius * sin(Math.toRadians(angle.toDouble())).toFloat()
            val dotSize = 4.dp.toPx() * (0.5f + (index % 3) * 0.25f)
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                radius = dotSize,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun ListeningCard(transcript: String, audioLevels: List<Float>) {
    GlassMorphicCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "LISTENING",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.62f),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = transcript.ifBlank { "Say your command..." },
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            WaveformVisualizer(audioLevels = audioLevels)
        }
    }
}

@Composable
private fun WaveformVisualizer(audioLevels: List<Float>) {
    Row(
        modifier = Modifier.fillMaxWidth(0.8f),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        audioLevels.takeLast(20).forEach { level ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((20 + level * 40).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0xFF0066FF))
                        )
                    )
            )
        }
    }
}

@Composable
private fun ThinkingCard() {
    GlassMorphicCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "THINKING",
                fontSize = 12.sp,
                color = Color(0xFF00E5FF),
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                repeat(3) { index ->
                    val scale by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 1200
                                0.6f at 0 with LinearEasing
                                1.2f at 600 with FastOutSlowInEasing
                                0.6f at 1200 with LinearEasing
                            },
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "thinkingDot$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp * scale)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF))
                    )
                }
            }
        }
    }
}

@Composable
private fun RespondingCard(text: String) {
    GlassMorphicCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "iKoo",
                fontSize = 12.sp,
                color = Color(0xFF00E5FF),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )
        }
    }
}

@Composable
private fun WakingCard() {
    GlassMorphicCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "WAKE",
                fontSize = 12.sp,
                color = Color(0xFF2FC7FF),
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Listening...", // Changed from "Hey iKoo"
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun GlassMorphicCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF07101F).copy(alpha = 0.88f))
            .blur(0.5.dp)
            .border(1.dp, Color(0xFF2FC7FF).copy(alpha = 0.24f), RoundedCornerShape(28.dp))
            .padding(26.dp)
    ) {
        content()
    }
}

@Composable
private fun EdgeLightBorder(
    progress: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val animatedOffset by rememberInfiniteTransition(label = "edgeLight").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "edgeOffset"
    )
    Canvas(modifier = modifier.padding(10.dp)) {
        // FIX 5: Increased stroke width from 5.dp to 8.dp
        val strokeWidth = 8.dp.toPx()
        val radius = 36.dp.toPx()
        val visibleAlpha = progress.coerceIn(0f, 1f)
        drawRoundRect(
            brush = Brush.sweepGradient(
                colors = colors + colors.first(),
                center = center
            ),
            topLeft = Offset(strokeWidth, strokeWidth),
            size = androidx.compose.ui.geometry.Size(
                size.width - strokeWidth * 2,
                size.height - strokeWidth * 2
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
            // FIX 5: Increased alpha from 0.35f to 0.70f
            alpha = (0.70f + animatedOffset * 0.30f) * visibleAlpha
        )
    }
}

// Updated buildResponse with actual app launching functionality
private fun buildResponse(context: android.content.Context, spoken: String): String {
    if (spoken.isBlank()) return "I'm ready. What can I help you with?"

    val lower = spoken.lowercase().trim()

    if (lower.containsAny("whatsapp", "what app", "message")) {
        openWhatsApp(context)
        return "Opening WhatsApp."
    }

    if (lower.containsAny("settings", "open settings", "permission")) {
        openAppSettings(context)
        return "Opening iKoo settings."
    }

    if (
        lower.containsAny(
            "calendar",
            "meeting",
            "schedule",
            "appointment",
            "remind",
            "dinner",
            "lunch",
            "party",
            "exam",
            "interview"
        )
    ) {
        openCalendarInsert(context, spoken)
        return "Opening calendar so you can save this event."
    }

    if (
        lower.containsAny(
            "gallery",
            "photo",
            "photos",
            "picture",
            "image",
            "screenshot",
            "selfie",
            "show image",
            "open image"
        )
    ) {
        openGallery(context)
        return "Opening gallery."
    }

    if (lower.containsAny("browser", "google", "search", "internet", "find online")) {
        openBrowser(context, spoken)
        return "Opening Google search."
    }

    if (lower.containsAny("hello", "hi", "hey", "what can you do", "help")) {
        return "Hey! I'm iKoo. Say: open WhatsApp, schedule meeting tomorrow at 5, open gallery, or search Google."
    }

    // Keep IntentDetectionStore for calendar detection as fallback
    if (lower.containsAny("meeting", "schedule", "appointment", "remind", "calendar",
            "call", "dinner", "lunch", "party", "exam", "interview")
    ) {
        val detection = IntentDetectionStore.update(
            packageName = "voice",
            visibleText = spoken
        )
        return detection.eventData?.let { event ->
            val parts = listOfNotNull(event.title, event.datePhrase, event.timePhrase)
            "Got it. I detected: ${parts.joinToString(", ")}. Check Assistive Touch to add it to your calendar."
        } ?: "I'll watch for that event. You can review detections in Assistive Touch."
    }

    return "I heard: \"$spoken\". Try: open WhatsApp, schedule dinner Friday 9 PM, open gallery, or search Google."
}

private fun openWhatsApp(context: android.content.Context) {
    val pm = context.packageManager

    val intent = pm.getLaunchIntentForPackage("com.whatsapp")
        ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")

    runCatching {
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.whatsapp")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun openGallery(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        type = "image/*"
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        context.startActivity(intent)
    }
}

private fun openBrowser(context: android.content.Context, query: String) {
    val clean = query
        .replace("search", "", ignoreCase = true)
        .replace("google", "", ignoreCase = true)
        .replace("internet", "", ignoreCase = true)
        .replace("browser", "", ignoreCase = true)
        .trim()
        .ifBlank { query }

    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/search?q=${Uri.encode(clean)}")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        context.startActivity(intent)
    }
}

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        context.startActivity(intent)
    }
}

private fun openCalendarInsert(context: android.content.Context, title: String) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, title.take(100))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        context.startActivity(intent)
    }
}

private fun String.containsAny(vararg words: String): Boolean {
    return words.any { contains(it) }
}
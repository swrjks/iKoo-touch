package com.sudocode.ikoo.ui.home

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.sudocode.ikoo.accessibility.IKooAccessibilityService
import com.sudocode.ikoo.calendar.CalendarActionManager
import com.sudocode.ikoo.intent.EventData
import com.sudocode.ikoo.intent.IntentDetectionStore
import com.sudocode.ikoo.intent.IntentType
import com.sudocode.ikoo.intent.LatestIntentDetection
import com.sudocode.ikoo.overlay.AssistiveTouchManager
import com.sudocode.ikoo.wakeword.HeyIKooService
import com.sudocode.ikoo.ui.assistant.VoiceAssistantOverlay
import com.sudocode.ikoo.ui.components.*
import kotlinx.coroutines.delay

// Color definitions for HomeScreen
private object IKooHomeColors {
    val background = Color(0xFF05060A)
    val onBackground = Color(0xFFF5F7FF)
    val onBackgroundSecondary = Color(0xFF9CA3B7)
    val primary = Color(0xFF00F5FF)   // ElectricMint
    val secondary = Color(0xFF3B82F6) // SignalBlue
    val accent = Color(0xFFA855F7)    // SoftViolet
    val onPrimary = Color.Black
    val error = Color(0xFFEF4444)     // WarmCoral
}

/**
 * Main home screen for iKoo AI Assistant
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 8.dp
) {
    var isAssistantVisible by remember { mutableStateOf(false) }

    val latestDetection by IntentDetectionStore.latestDetection.collectAsState()
    val context = LocalContext.current

    val assistiveTouchManager = remember(context) {
        AssistiveTouchManager(context.applicationContext)
    }

    // Start voice recognition service if permission granted
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HeyIKooService::class.java)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IKooHomeColors.background)
    ) {
        AnimatedBackgroundGradient()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AppHeader(
                    onLogoClick = {},
                    onAssistantClick = { isAssistantVisible = true }
                )
            }

            item {
                StatusCard(
                    isActive = latestDetection.result.type != IntentType.NONE,
                    latestDetection = latestDetection
                )
            }

            item {
                PermissionChecklistCard()
            }

            item {
                OverlayPermissionCard()
            }

            item {
                AssistiveTouchControlCard(
                    assistiveTouchManager = assistiveTouchManager
                )
            }

            item {
                ExtractedEventCard(eventData = latestDetection.eventData)
            }

            item {
                PrivacyInfoCard()
            }

            item {
                Spacer(modifier = Modifier.height(bottomPadding))
            }
        }

        if (isAssistantVisible) {
            VoiceAssistantOverlay(
                onClose = { isAssistantVisible = false }
            )
        }
    }
}

@Composable
private fun AppHeader(
    onLogoClick: () -> Unit,
    onAssistantClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val logoRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logoRotation"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer { rotationZ = logoRotation }
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            IKooHomeColors.primary,
                            IKooHomeColors.secondary,
                            IKooHomeColors.accent
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLogoClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "iKoo",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                ),
                color = IKooHomeColors.onPrimary
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "iKoo",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = IKooHomeColors.onBackground,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "On-device AI assistant",
                fontSize = 13.sp,
                color = IKooHomeColors.onBackgroundSecondary
            )
        }

        var isPressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.92f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "assistantScale"
        )

        IconButton(
            onClick = onAssistantClick,
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(
                    IKooHomeColors.primary.copy(alpha = 0.15f),
                    CircleShape
                )
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Voice Assistant",
                tint = IKooHomeColors.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun StatusCard(
    isActive: Boolean,
    latestDetection: LatestIntentDetection
) {
    val statusColor by animateColorAsState(
        targetValue = if (isActive) IKooHomeColors.primary else IKooHomeColors.secondary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "statusColor"
    )

    var animatedConfidence by remember { mutableStateOf(0) }

    LaunchedEffect(latestDetection.result.confidence) {
        val targetConfidence = (latestDetection.result.confidence * 100).toInt()
        for (i in 0..targetConfidence step maxOf(1, targetConfidence / 20)) {
            animatedConfidence = i
            delay(8)
        }
    }

    PremiumCard(
        variant = CardVariant.GAMING,
        cornerRadius = 32,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedStatusOrb(
                active = isActive,
                modifier = Modifier.size(80.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "iKoo Status",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = IKooHomeColors.onBackgroundSecondary,
                        letterSpacing = 1.sp
                    )

                    StatusChip(
                        text = if (isActive) "ACTIVE" else "STANDBY",
                        color = statusColor,
                        isAnimated = isActive
                    )
                }

                AnimatedContent(
                    targetState = isActive,
                    transitionSpec = {
                        fadeIn() + slideInVertically() togetherWith
                                fadeOut() + slideOutVertically()
                    },
                    label = "statusTextAnimation"
                ) { active ->
                    Text(
                        text = if (active) "Scanning actively" else "Ready to assist",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = IKooHomeColors.onBackground
                    )
                }

                Text(
                    text = when {
                        latestDetection.result.type != IntentType.NONE ->
                            "Detected: ${latestDetection.result.type.displayName()}"
                        else -> "Enable accessibility to analyze screen content"
                    },
                    fontSize = 12.sp,
                    color = IKooHomeColors.onBackgroundSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                ConfidenceIndicator(
                    confidence = animatedConfidence,
                    color = IKooHomeColors.primary
                )
            }
        }
    }
}

@Composable
private fun ConfidenceIndicator(
    confidence: Int,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Confidence",
                fontSize = 10.sp,
                color = IKooHomeColors.onBackgroundSecondary
            )
            Text(
                text = "$confidence%",
                fontSize = 10.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(confidence / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                IKooHomeColors.primary,
                                IKooHomeColors.secondary
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun PermissionChecklistCard() {
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(context.isIKooAccessibilityEnabled()) }
    var isOverlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAirplaneModeOn by remember { mutableStateOf(context.isAirplaneModeOn()) }

    RefreshOnResume(context) {
        isAccessibilityEnabled = context.isIKooAccessibilityEnabled()
        isOverlayEnabled = Settings.canDrawOverlays(context)
        isAirplaneModeOn = context.isAirplaneModeOn()
    }

    PremiumCard(
        variant = CardVariant.PREMIUM,
        cornerRadius = 28
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Permissions",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = IKooHomeColors.onBackground
                )

                StatusChip(
                    text = "REQUIRED",
                    color = IKooHomeColors.primary,
                    isAnimated = true
                )
            }

            PermissionChecklistItem(
                label = "Accessibility Service",
                isChecked = isAccessibilityEnabled,
                isRequired = true
            )

            PermissionChecklistItem(
                label = "Overlay Permission",
                isChecked = isOverlayEnabled,
                isRequired = true
            )

            PermissionChecklistItem(
                label = "On-device AI Model",
                isChecked = true,
                isRequired = true
            )

            PermissionChecklistItem(
                label = "Airplane Mode",
                isChecked = isAirplaneModeOn,
                isRequired = false
            )
        }
    }
}

@Composable
private fun PermissionChecklistItem(
    label: String,
    isChecked: Boolean,
    isRequired: Boolean
) {
    val indicatorColor by animateColorAsState(
        targetValue = when {
            isChecked -> IKooHomeColors.primary
            isRequired -> IKooHomeColors.error
            else -> IKooHomeColors.secondary
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "checklistColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(indicatorColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isChecked) "✓" else "!",
                    fontSize = 12.sp,
                    color = indicatorColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = label,
                fontSize = 13.sp,
                color = IKooHomeColors.onBackground
            )
        }

        Text(
            text = when {
                isChecked -> "Ready"
                isRequired -> "Required"
                else -> "Optional"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = indicatorColor
        )
    }
}

@Composable
private fun OverlayPermissionCard() {
    val context = LocalContext.current
    var isOverlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    RefreshOnResume(context) {
        isOverlayEnabled = Settings.canDrawOverlays(context)
    }

    PremiumCard(
        variant = CardVariant.PREMIUM,
        cornerRadius = 28
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Overlay Permission",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = IKooHomeColors.onBackground
                )

                StatusChip(
                    text = if (isOverlayEnabled) "GRANTED" else "REQUIRED",
                    color = if (isOverlayEnabled) IKooHomeColors.primary else IKooHomeColors.error,
                    isAnimated = !isOverlayEnabled
                )
            }

            Text(
                text = "Required for floating assistant bubble and screen overlay suggestions",
                fontSize = 12.sp,
                color = IKooHomeColors.onBackgroundSecondary
            )

            if (!isOverlayEnabled) {
                ActionButton(
                    text = "Grant Overlay Permission",
                    icon = Icons.Default.Settings,
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AssistiveTouchControlCard(
    assistiveTouchManager: AssistiveTouchManager
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var isOverlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibilityEnabled by remember { mutableStateOf(context.isIKooAccessibilityEnabled()) }
    var isFloatingBubbleVisible by remember { mutableStateOf(assistiveTouchManager.isShowing()) }

    RefreshOnResume(context) {
        isOverlayEnabled = Settings.canDrawOverlays(context)
        isAccessibilityEnabled = context.isIKooAccessibilityEnabled()
        isFloatingBubbleVisible = assistiveTouchManager.isShowing()
    }

    PremiumCard(
        variant = if (isFloatingBubbleVisible) CardVariant.GAMING else CardVariant.PREMIUM,
        cornerRadius = 28,
        glowEnabled = isFloatingBubbleVisible
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FloatingBubblePreview(isActive = isFloatingBubbleVisible)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Floating Assistant",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = IKooHomeColors.onBackground
                    )
                    Text(
                        text = "Quick access bubble for iKoo",
                        fontSize = 12.sp,
                        color = IKooHomeColors.onBackgroundSecondary
                    )
                }

                Switch(
                    checked = isFloatingBubbleVisible,
                    onCheckedChange = { shouldShow ->
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                        if (shouldShow && !Settings.canDrawOverlays(context)) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else if (shouldShow) {
                            assistiveTouchManager.show()
                            isFloatingBubbleVisible = true
                        } else {
                            assistiveTouchManager.hide()
                            isFloatingBubbleVisible = false
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = IKooHomeColors.onPrimary,
                        checkedTrackColor = IKooHomeColors.primary,
                        uncheckedThumbColor = IKooHomeColors.onBackground,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.20f)
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RequirementChip(
                    label = "Accessibility",
                    isMet = isAccessibilityEnabled,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )

                RequirementChip(
                    label = "Overlay",
                    isMet = isOverlayEnabled,
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                )
            }

            Text(
                text = when {
                    isOverlayEnabled && isAccessibilityEnabled ->
                        "Bubble is ready. Accessibility enables screen scan suggestions."
                    isOverlayEnabled && !isAccessibilityEnabled ->
                        "Bubble can turn on now. Enable Accessibility to scan app screens."
                    else ->
                        "Overlay permission is required to show the floating bubble."
                },
                fontSize = 12.sp,
                color = IKooHomeColors.onBackgroundSecondary,
                lineHeight = 18.sp
            )

            ActionButton(
                text = when {
                    isFloatingBubbleVisible -> "Hide Floating Bubble"
                    isOverlayEnabled -> "Show Floating Bubble"
                    else -> "Enable Overlay Permission"
                },
                icon = when {
                    isFloatingBubbleVisible -> Icons.Default.VisibilityOff
                    isOverlayEnabled -> Icons.Default.Visibility
                    else -> Icons.Default.OpenInNew
                },
                onClick = {
                    if (!Settings.canDrawOverlays(context)) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    } else if (isFloatingBubbleVisible) {
                        assistiveTouchManager.hide()
                        isFloatingBubbleVisible = false
                    } else {
                        assistiveTouchManager.show()
                        isFloatingBubbleVisible = true
                    }
                }
            )
        }
    }
}

@Composable
private fun FloatingBubblePreview(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "previewPulse"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1C1C20).copy(alpha = 0.92f),
                        Color(0xFF30303A).copy(alpha = 0.76f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size((48 * pulse).dp)
                    .clip(CircleShape)
                    .background(IKooHomeColors.primary.copy(alpha = 0.1f))
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (isActive) 0.15f else 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            Brush.linearGradient(
                                listOf(
                                    IKooHomeColors.primary,
                                    IKooHomeColors.secondary,
                                    IKooHomeColors.accent
                                )
                            )
                        } else {
                            Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.5f)))
                        }
                    )
            )
        }
    }
}

@Composable
private fun RequirementChip(label: String, isMet: Boolean, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(),
        label = "reqChipScale"
    )

    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isMet) IKooHomeColors.primary else IKooHomeColors.error)
        )
        Text(
            label,
            fontSize = 12.sp,
            color = if (isMet) IKooHomeColors.onBackground else IKooHomeColors.onBackgroundSecondary
        )
        Text(
            if (isMet) "✓" else "→",
            fontSize = 11.sp,
            color = if (isMet) IKooHomeColors.primary else IKooHomeColors.secondary
        )
    }
}

@Composable
private fun ExtractedEventCard(eventData: EventData?) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = eventData != null,
        enter = fadeIn() + slideInVertically { it / 3 },
        exit = fadeOut() + slideOutVertically { -it / 3 }
    ) {
        eventData?.let { event ->
            var isExpanded by remember { mutableStateOf(false) }

            PremiumCard(
                variant = CardVariant.GAMING,
                cornerRadius = 28,
                modifier = Modifier.fillMaxWidth(),
                onClick = { isExpanded = !isExpanded }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXTRACTED EVENT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IKooHomeColors.onBackgroundSecondary,
                            letterSpacing = 1.sp
                        )

                        ConfidenceBadge(confidence = event.confidence)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            IKooHomeColors.accent,
                                            IKooHomeColors.secondary,
                                            IKooHomeColors.primary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = event.datePhrase?.take(2)?.uppercase() ?: "EV",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = IKooHomeColors.onPrimary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IKooHomeColors.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = event.summaryLine(),
                                fontSize = 12.sp,
                                color = IKooHomeColors.onBackgroundSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = IKooHomeColors.onBackgroundSecondary
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            EventDetailRow(
                                icon = "📅",
                                label = "Date",
                                value = event.datePhrase
                            )

                            EventDetailRow(
                                icon = "⏰",
                                label = "Time",
                                value = event.timePhrase
                            )

                            EventDetailRow(
                                icon = "📍",
                                label = "Location",
                                value = event.location
                            )

                            ActionButton(
                                text = "Add to Calendar",
                                icon = Icons.Default.CalendarMonth,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    CalendarActionManager.openCalendarInsert(context, event)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventDetailRow(icon: String, label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon, fontSize = 14.sp)
            Text(label, fontSize = 12.sp, color = IKooHomeColors.onBackgroundSecondary)
        }
        Text(
            value ?: "—",
            fontSize = 13.sp,
            fontWeight = if (value != null) FontWeight.Medium else FontWeight.Normal,
            color = if (value != null) IKooHomeColors.onBackground else IKooHomeColors.onBackgroundSecondary.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun PrivacyInfoCard() {
    val context = LocalContext.current
    var isAirplaneModeOn by remember { mutableStateOf(context.isAirplaneModeOn()) }

    RefreshOnResume(context) {
        isAirplaneModeOn = context.isAirplaneModeOn()
    }

    PremiumCard(
        variant = CardVariant.PREMIUM,
        cornerRadius = 28
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    IKooHomeColors.primary,
                                    IKooHomeColors.secondary.copy(alpha = 0.28f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Privacy",
                        tint = IKooHomeColors.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privacy First",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = IKooHomeColors.onBackground
                    )
                    Text(
                        text = "All processing stays on your device",
                        fontSize = 12.sp,
                        color = IKooHomeColors.onBackgroundSecondary
                    )
                }

                StatusChip(
                    text = if (isAirplaneModeOn) "AIRPLANE" else "ONLINE",
                    color = if (isAirplaneModeOn) IKooHomeColors.primary else IKooHomeColors.secondary,
                    isAnimated = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrivacyMetric(
                    icon = "🔒",
                    label = "Encryption",
                    value = "End-to-end"
                )
                PrivacyMetric(
                    icon = "📡",
                    label = "Network",
                    value = if (isAirplaneModeOn) "Offline" else "Local only"
                )
                PrivacyMetric(
                    icon = "☁️",
                    label = "Cloud",
                    value = "0 KB"
                )
            }
        }
    }
}

@Composable
private fun PrivacyMetric(icon: String, label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, fontSize = 16.sp)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = IKooHomeColors.onBackground)
        Text(label, fontSize = 9.sp, color = IKooHomeColors.onBackgroundSecondary)
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = IKooHomeColors.onBackgroundSecondary
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f, fill = false),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "actionButtonScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            IKooHomeColors.primary,
                            IKooHomeColors.secondary
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            IKooHomeColors.onBackgroundSecondary,
                            IKooHomeColors.onBackgroundSecondary.copy(alpha = 0.5f)
                        )
                    )
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) IKooHomeColors.onPrimary else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) IKooHomeColors.onPrimary else Color.White.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun AnimatedBackgroundGradient() {
    val infiniteTransition = rememberInfiniteTransition()
    val offsetX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgX"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgY"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(350.dp)
                .graphicsLayer { translationX = -90f; translationY = -120f }
                .blur(80.dp)
                .clip(CircleShape)
                .background(IKooHomeColors.accent.copy(alpha = 0.4f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(300.dp)
                .graphicsLayer { translationX = 130f; translationY = -40f }
                .blur(80.dp)
                .clip(CircleShape)
                .background(IKooHomeColors.secondary.copy(alpha = 0.35f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(280.dp)
                .graphicsLayer { translationX = -70f; translationY = 80f }
                .blur(80.dp)
                .clip(CircleShape)
                .background(IKooHomeColors.primary.copy(alpha = 0.25f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            IKooHomeColors.background.copy(alpha = 0.1f),
                            IKooHomeColors.background.copy(alpha = 0.8f),
                            IKooHomeColors.background
                        ),
                        center = Offset(offsetX, offsetY),
                        radius = 600f
                    )
                )
        )
    }
}

@Composable
private fun StatusChip(text: String, color: Color, isAnimated: Boolean = false) {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "chipPulse"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = if (isAnimated) 0.15f * pulse else 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ConfidenceBadge(confidence: Float) {
    val percent = (confidence * 100).toInt()
    var displayedPercent by remember { mutableStateOf(0) }
    val color = when {
        percent >= 80 -> IKooHomeColors.primary
        percent >= 50 -> Color(0xFFFFB347)
        else -> IKooHomeColors.error
    }

    LaunchedEffect(percent) {
        for (i in 0..percent step maxOf(1, percent / 20)) {
            displayedPercent = i
            delay(10)
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            "$displayedPercent%",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// Extension functions
private fun IntentType.displayName(): String {
    return when (this) {
        IntentType.CALENDAR_EVENT -> "Calendar Event"
        IntentType.REMINDER -> "Reminder"
        IntentType.TASK -> "Task"
        IntentType.NONE -> "No Intent"
    }
}

private fun EventData.summaryLine(): String {
    return listOfNotNull(datePhrase, timePhrase, location?.let { "at $it" })
        .joinToString(" • ")
        .ifBlank { "Ready to add to calendar" }
}

@Composable
private fun RefreshOnResume(
    context: android.content.Context,
    refresh: () -> Unit
) {
    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(context, lifecycleOwner) {
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
}

private fun android.content.Context.isAirplaneModeOn(): Boolean {
    return Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
}

private fun android.content.Context.isIKooAccessibilityEnabled(): Boolean {
    val accessibilityEnabled = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
    val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val expectedService = ComponentName(this, IKooAccessibilityService::class.java)
    val serviceEnabled = enabledServices.split(':').mapNotNull(ComponentName::unflattenFromString).any {
        it.packageName == expectedService.packageName && it.className == expectedService.className
    }
    return accessibilityEnabled && serviceEnabled
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        typography = Typography()
    ) {
        HomeScreen()
    }
}

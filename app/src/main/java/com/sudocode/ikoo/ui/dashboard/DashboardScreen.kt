package com.sudocode.ikoo.ui.dashboard

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudocode.ikoo.intent.IntentDetectionStore
import com.sudocode.ikoo.intent.IntentType
import com.sudocode.ikoo.intent.LatestIntentDetection
import com.sudocode.ikoo.accessibility.IKooAccessibilityService
import com.sudocode.ikoo.assistant.VoiceOverlayActivity
import com.sudocode.ikoo.ui.components.PremiumCard
import com.sudocode.ikoo.ui.components.CardVariant
import com.sudocode.ikoo.ui.theme.*
import com.sudocode.ikoo.usage.AppUsageLimiter
import java.util.Calendar
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val latestDetection by IntentDetectionStore.latestDetection.collectAsState()
    val history by IntentDetectionStore.detectionHistory.collectAsState()
    val liveActivities = remember(latestDetection, history) {
        (listOf(latestDetection) + history)
            .filter { it.visibleText.isNotBlank() && System.currentTimeMillis() - it.detectedAtMillis <= FIVE_MINUTES_MILLIS }
            .distinctBy { "${it.packageName}|${it.visibleText}|${it.detectedAtMillis}" }
            .take(8)
    }
    val haptic = LocalHapticFeedback.current
    var selectedQuickAction by remember { mutableStateOf<String?>(null) }
    var showActionFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }

    // Animated mesh background with enhanced colors
    AnimatedMeshBackground()

    // Quick action feedback
    LaunchedEffect(selectedQuickAction) {
        if (selectedQuickAction != null) {
            showActionFeedback = true
            feedbackMessage = when (selectedQuickAction) {
                "Gallery Search" -> "Opening Gallery Search..."
                "Create Reminder" -> "Creating new reminder..."
                "Scan Screen" -> "Analyzing screen content..."
                "Ask iKoo" -> "Starting voice assistant..."
                else -> "Action triggered"
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(1500)
            showActionFeedback = false
            selectedQuickAction = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with AI Status
            item { AICommandCenterHeader() }

            // Assistant Status Card
            item { AssistantStatusCard(latestDetection, liveActivities.size) }

            item { AppUsageTrackingCard() }

            // Useful Quick Actions
            item { QuickActionsRow(onActionClick = { selectedQuickAction = it }) }

            // Smart Insights
            item { SmartInsightsCard(latestDetection = latestDetection, history = history) }

            // Performance metrics were removed because they showed static demo
            // values and added unnecessary animation work on the dashboard.
        }

        // Quick Action Feedback Overlay
        AnimatedVisibility(
            visible = showActionFeedback,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showActionFeedback = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                PremiumCard(
                    variant = CardVariant.GAMING,
                    cornerRadius = 28,
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(0.8f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", fontSize = 20.sp, color = Color(0xFF00E5FF))
                        }
                        Text(
                            feedbackMessage,
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AICommandCenterHeader() {
    var isOnline by remember { mutableStateOf(true) }
    val infiniteTransition = rememberInfiniteTransition()

    // Animated gradient for header text
    val hueRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "headerHue"
    )

    val gradientColors = listOf(
        Color.White,
        Color.hsv(hueRotation, 0.8f, 1f),
        Color(0xFF00E5FF),
        Color.White
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AI Command",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Center",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = ElectricMint,
                    letterSpacing = (-0.5).sp
                )
            }

            // Enhanced Status Indicator with pulse
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val pulse by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                    label = "statusPulse"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { alpha = pulse }
                        .clip(CircleShape)
                        .background(
                            if (isOnline) Brush.linearGradient(
                                colors = listOf(Color(0xFF00E5FF), Color(0xFF9B30FF))
                            ) else SolidColor(Color.Gray)
                        )
                )
                Text(
                    text = if (isOnline) "ONLINE" else "OFFLINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOnline) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun AssistantStatusCard(
    detection: com.sudocode.ikoo.intent.LatestIntentDetection,
    liveCount: Int
) {
    PremiumCard(
        variant = CardVariant.GAMING,
        glowEnabled = true,
        cornerRadius = 32
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with animated status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ASSISTANT STATUS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.5.sp
                )
                AnimatedChip(
                    text = "ACTIVE",
                    color = Color(0xFF00E5FF),
                    animated = true
                )
            }

            // Animated stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusMetric(
                    label = "LAST 5 MIN",
                    value = liveCount.toString(),
                    subValue = "activities",
                    modifier = Modifier.weight(1f)
                )
                StatusMetric(
                    label = "LATEST",
                    value = detection.result.type.displayName(),
                    subValue = detection.sourceLabel(),
                    modifier = Modifier.weight(1f)
                )
                StatusMetric(
                    label = "LATENCY",
                    value = "${detection.latencyMillis}ms",
                    subValue = if (detection.offline) "offline" else "online",
                    modifier = Modifier.weight(1f)
                )
            }

            // Enhanced Confidence Bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Detection Confidence",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp
                    )
                    AnimatedCounter(
                        value = (detection.result.confidence * 100).toInt(),
                        color = Color(0xFF00E5FF)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Animated confidence bar with glow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    val fillWidth by animateFloatAsState(
                        targetValue = detection.result.confidence,
                        animationSpec = tween(1000, easing = FastOutSlowInEasing),
                        label = "confidenceFill"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF00E5FF), Color(0xFF1478FF), Color(0xFF6A6BFF))
                                )
                            )
                            .graphicsLayer {
                                shadowElevation = 4f
                                spotShadowColor = Color(0xFF00E5FF)
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMetric(
    label: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 1.sp
        )
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            subValue,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun AnimatedCounter(value: Int, color: Color) {
    var displayedValue by remember { mutableStateOf(0) }

    LaunchedEffect(value) {
        for (i in 0..value step maxOf(1, value / 20)) {
            displayedValue = i
            delay(16)
        }
        displayedValue = value
    }

    Text(
        "$displayedValue%",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun LiveActivityWindow(activities: List<com.sudocode.ikoo.intent.LatestIntentDetection>) {
    val featuredActivity = remember(activities) {
        activities.firstOrNull { it.result.type == IntentType.CALENDAR_EVENT && it.eventData != null }
            ?: activities.firstOrNull { it.result.type != IntentType.NONE }
            ?: activities.firstOrNull()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "LAST 5 MINUTES", icon = "LIVE")

        if (featuredActivity == null) {
            PremiumCard(variant = CardVariant.MINIMAL, cornerRadius = 24) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No live activity",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Open Gmail, WhatsApp, Messages, or tap Scan Screen.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LiveActivityRow(
                action = featuredActivity,
                modifier = Modifier.animateContentSize()
            )
        }
    }
}

@Composable
private fun LiveActivityRow(
    action: com.sudocode.ikoo.intent.LatestIntentDetection,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val intentColor = when (action.result.type) {
        IntentType.CALENDAR_EVENT -> Color(0xFF00E5FF)
        IntentType.REMINDER -> Color(0xFFFF1493)
        IntentType.TASK -> Color(0xFF9B30FF)
        IntentType.NONE -> Color.Gray
    }

    PremiumCard(
        modifier = modifier,
        variant = CardVariant.MINIMAL,
        glowEnabled = false,
        cornerRadius = 20,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            isExpanded = !isExpanded
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StaticAppIcon(type = action.result.type, color = intentColor)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        action.sourceLabel(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.52f),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        action.liveSummary().take(if (isExpanded) 140 else 72),
                        fontSize = if (isExpanded) 14.sp else 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = if (isExpanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ConfidenceBadge(confidence = action.result.confidence)
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DetailChip(label = "Latency", value = "${action.latencyMillis}ms")
                        DetailChip(label = "Mode", value = if (action.offline) "Offline" else "Online")
                        DetailChip(label = "Age", value = action.ageLabel())
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageTrackingCard() {
    val context = LocalContext.current
    val installedApps = remember { context.installedLaunchableApps() }
    var savedLimit by remember { mutableStateOf(AppUsageLimiter.readLimit(context)) }
    var selectedApp by remember(savedLimit, installedApps) {
        mutableStateOf(
            installedApps.firstOrNull { it.packageName == savedLimit?.packageName }
                ?: installedApps.firstOrNull()
        )
    }
    var minutes by remember(savedLimit) { mutableStateOf(savedLimit?.limitMinutes ?: 30) }
    var expanded by remember { mutableStateOf(false) }
    val usageMillis = remember(selectedApp, savedLimit) {
        selectedApp?.let { runCatching { AppUsageLimiter.todayUsageMillis(context, it.packageName) }.getOrDefault(0L) } ?: 0L
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "APP USAGE TRACKING", icon = "LIMIT")

        PremiumCard(variant = CardVariant.PREMIUM, glowEnabled = false, cornerRadius = 24) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Set a daily limit. When the app crosses it, iKoo shows an exit prompt.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.58f),
                    lineHeight = 17.sp
                )

                AppSelectorField(
                    selectedApp = selectedApp,
                    onClick = { expanded = true }
                )

                if (expanded) {
                    AppPickerDialog(
                        apps = installedApps,
                        selectedPackage = selectedApp?.packageName,
                        onDismiss = { expanded = false },
                        onSelect = { app ->
                            selectedApp = app
                            expanded = false
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MinuteStepButton(label = "-5") { minutes = (minutes - 5).coerceAtLeast(5) }
                    Text(
                        "${minutes}m",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = ElectricMint
                    )
                    MinuteStepButton(label = "+5") { minutes = (minutes + 5).coerceAtMost(240) }
                }

                Text(
                    "Used today: ${usageMillis.formatDuration()}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.62f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = selectedApp != null,
                        onClick = {
                            selectedApp?.let {
                                AppUsageLimiter.saveLimit(context, it.packageName, minutes)
                                savedLimit = AppUsageLimiter.readLimit(context)
                            }
                        }
                    ) {
                        Text(if (savedLimit == null) "Set limit" else "Update")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            AppUsageLimiter.clearLimit(context)
                            savedLimit = null
                        }
                    ) {
                        Text("Clear")
                    }
                }

                Text(
                    "Needs Usage Access and iKoo Accessibility enabled.",
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    fontSize = 11.sp,
                    color = SignalBlue
                )
            }
        }
    }
}

@Composable
private fun AppSelectorField(
    selectedApp: LaunchableApp?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF07182C), Color(0xFF03101E))
                )
            )
            .border(1.dp, Color(0xFF148BFF).copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppInitialBadge(label = selectedApp?.label ?: "A")
        Column(modifier = Modifier.weight(1f)) {
            Text("LIMITED APP", fontSize = 10.sp, color = Color.White.copy(alpha = 0.42f), letterSpacing = 0.8.sp)
            Text(
                selectedApp?.label ?: "Choose app",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("CHANGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElectricMint)
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<LaunchableApp>,
    selectedPackage: String?,
    onDismiss: () -> Unit,
    onSelect: (LaunchableApp) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose app", color = Color.White, fontWeight = FontWeight.Black)
                Text("Select one app to limit", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 390.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(apps.take(80), key = { it.packageName }) { app ->
                    val selected = app.packageName == selectedPackage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Color(0xFF0A2A48) else Color(0xFF061320))
                            .border(
                                1.dp,
                                if (selected) ElectricMint.copy(alpha = 0.75f) else Color(0xFF148BFF).copy(alpha = 0.22f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelect(app) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppInitialBadge(label = app.label, compact = true)
                        Text(
                            app.label,
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selected) {
                            Text("SET", color = ElectricMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SignalBlue)
            }
        },
        containerColor = Color(0xFF020914),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun AppInitialBadge(label: String, compact: Boolean = false) {
    val size = if (compact) 34.dp else 42.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(if (compact) 12.dp else 14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF168BFF), Color(0xFF00E5FF)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.firstOrNull()?.uppercaseChar()?.toString() ?: "A",
            color = Color.White,
            fontSize = if (compact) 14.sp else 17.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun MinuteStepButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.width(82.dp),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StaticAppIcon(type: IntentType, color: Color) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.1f))
                )
            )
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            when (type) {
                IntentType.CALENDAR_EVENT -> "EV"
                IntentType.REMINDER -> "RM"
                IntentType.TASK -> "TK"
                IntentType.NONE -> "AI"
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

@Composable
private fun ConfidenceBadge(confidence: Float) {
    val percent = (confidence * 100).toInt()
    val color = when {
        percent >= 80 -> Color(0xFF00E5FF)
        percent >= 50 -> Color(0xFFFFB347)
        else -> Color(0xFFFF1493)
    }

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "badgeScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            "$percent%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun QuickActionsRow(onActionClick: (String) -> Unit) {
    val context = LocalContext.current
    SectionHeader(title = "QUICK ACTIONS", icon = "TOOLS")
    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EnhancedQuickActionButton(
            label = "Scan",
            subLabel = "screen",
            color = ElectricMint,
            modifier = Modifier.weight(1f),
            onClick = {
                context.sendBroadcast(Intent(IKooAccessibilityService.ACTION_SCAN_CURRENT_SCREEN))
                onActionClick("Scan Screen")
            }
        )
        EnhancedQuickActionButton(
            label = "Ask",
            subLabel = "voice",
            color = SignalBlue,
            modifier = Modifier.weight(1f),
            onClick = {
                context.startActivity(
                    Intent(context, VoiceOverlayActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                onActionClick("Ask iKoo")
            }
        )
        EnhancedQuickActionButton(
            label = "Notify",
            subLabel = "access",
            color = SoftViolet,
            modifier = Modifier.weight(1f),
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                onActionClick("Notification Access")
            }
        )
    }
}

@Composable
private fun EnhancedQuickActionButton(
    label: String,
    subLabel: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "actionScale"
    )

    PremiumCard(
        modifier = modifier.height(78.dp),
        variant = CardVariant.GAMING,
        glowEnabled = false,
        cornerRadius = 18,
        contentPadding = PaddingValues(0.dp),
        onClick = {
            isPressed = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                subLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SmartInsightsCard(
    latestDetection: LatestIntentDetection,
    history: List<LatestIntentDetection>
) {
    val context = LocalContext.current
    val usageSnapshot = remember { context.todayUsageSnapshot() }
    val detections = remember(latestDetection, history) {
        (listOf(latestDetection) + history).distinctBy {
            "${it.packageName}|${it.visibleText}|${it.detectedAtMillis}"
        }
    }
    val calendarEvents = detections.filter { it.result.type == IntentType.CALENDAR_EVENT && it.eventData != null }
    val accuracy = detections
        .filter { it.result.type != IntentType.NONE }
        .map { it.result.confidence }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.let { "${(it * 100).toInt()}%" }
        ?: "No data yet"
    val nextEvent = calendarEvents
        .mapNotNull { it.eventData }
        .firstOrNull()
        ?.timePhrase
        ?.let { "Next: $it" }
        ?: "Waiting"

    val insights = listOf(
        Insight(
            icon = Icons.Default.PhoneAndroid,
            label = "Screen Time Today",
            value = usageSnapshot.screenTimeLabel,
            trend = usageSnapshot.screenTimeTrend,
            actionable = !usageSnapshot.hasUsageAccess
        ),
        Insight(
            icon = Icons.Default.Apps,
            label = "Most Used App",
            value = usageSnapshot.topAppLabel,
            trend = usageSnapshot.topAppTrend,
            actionable = !usageSnapshot.hasUsageAccess
        ),
        Insight(
            icon = Icons.Default.CalendarMonth,
            label = "Upcoming Meetings",
            value = if (calendarEvents.isEmpty()) "No suggestions" else "${calendarEvents.size} found",
            trend = nextEvent,
            actionable = false
        ),
        Insight(
            icon = Icons.Default.TrackChanges,
            label = "Intent Accuracy",
            value = accuracy,
            trend = if (detections.isEmpty()) "Scan apps" else "${detections.size} detections",
            actionable = false
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "SMART INSIGHTS", icon = "▣")

        PremiumCard(variant = CardVariant.PREMIUM, cornerRadius = 32) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                insights.forEachIndexed { index, insight ->
                    AnimatedInsightRow(
                        insight = insight,
                        delay = index * 100L,
                        onClick = if (insight.actionable) {
                            {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        } else null
                    )
                    if (index < insights.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedInsightRow(
    insight: Insight,
    delay: Long,
    onClick: (() -> Unit)? = null
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delay)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInHorizontally()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SignalBlue.copy(alpha = 0.14f))
                        .border(1.dp, ElectricMint.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        insight.icon,
                        contentDescription = null,
                        tint = ElectricMint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        insight.label,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        insight.value,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            val trendColor = if (insight.actionable) SignalBlue else ElectricMint

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(trendColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    insight.trend,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = trendColor
                )
            }
        }
    }
}

@Composable
private fun PerformanceMetricsCard() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "PERFORMANCE METRICS", icon = "📈")

        PremiumCard(variant = CardVariant.GAMING, cornerRadius = 32) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // FPS Monitor
                PerformanceMetricRow(
                    label = "Frame Rate",
                    value = "60",
                    unit = "FPS",
                    color = Color(0xFF00E5FF),
                    progress = 1f
                )
                // CPU Usage
                PerformanceMetricRow(
                    label = "CPU Usage",
                    value = "23",
                    unit = "%",
                    color = Color(0xFF9B30FF),
                    progress = 0.23f
                )
                // Memory Usage
                PerformanceMetricRow(
                    label = "Memory Usage",
                    value = "342",
                    unit = "MB",
                    color = Color(0xFFFF1493),
                    progress = 0.342f
                )
            }
        }
    }
}

@Composable
private fun PerformanceMetricRow(
    label: String,
    value: String,
    unit: String,
    color: Color,
    progress: Float
) {
    var animatedProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(progress) {
        animate(
            initialValue = 0f,
            targetValue = progress,
            animationSpec = tween(1000, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animatedProgress = value
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                "$value$unit",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 16.sp)
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
private fun AnimatedChip(text: String, color: Color, animated: Boolean = false) {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "chipPulse"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = if (animated) 0.15f * pulse else 0.15f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
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
private fun DetailChip(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$label:",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.4f)
        )
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF00E5FF)
        )
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

@Composable
private fun AnimatedMeshBackground() {
    val infiniteTransition = rememberInfiniteTransition()
    val offsetX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshX"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshY"
    )

    val color1 = Color(0xFF1a0033)
    val color2 = Color(0xFF001a33)
    val color3 = Color(0xFF33001a)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val gradient1 = Brush.radialGradient(
                    colors = listOf(color1.copy(alpha = 0.6f), Color.Transparent),
                    center = Offset(offsetX, offsetY),
                    radius = 600f
                )
                val gradient2 = Brush.radialGradient(
                    colors = listOf(color2.copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(size.width - offsetX, size.height - offsetY),
                    radius = 500f
                )
                val gradient3 = Brush.radialGradient(
                    colors = listOf(color3.copy(alpha = 0.3f), Color.Transparent),
                    center = Offset(offsetY, size.width - offsetX),
                    radius = 450f
                )

                drawRect(brush = gradient1)
                drawRect(brush = gradient2)
                drawRect(brush = gradient3)
                drawRect(color = Color.Black.copy(alpha = 0.7f))
            }
    )
}

// Data classes
data class Insight(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val trend: String,
    val actionable: Boolean
)

private data class UsageInsightSnapshot(
    val hasUsageAccess: Boolean,
    val screenTimeLabel: String,
    val screenTimeTrend: String,
    val topAppLabel: String,
    val topAppTrend: String
)

private data class LaunchableApp(
    val label: String,
    val packageName: String
)

private fun Context.installedLaunchableApps(): List<LaunchableApp> {
    val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return packageManager.queryIntentActivities(launchIntent, 0)
        .mapNotNull { resolveInfo ->
            val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
            if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && resolveInfo.activityInfo.packageName == packageName) {
                return@mapNotNull null
            }
            LaunchableApp(
                label = resolveInfo.loadLabel(packageManager).toString(),
                packageName = resolveInfo.activityInfo.packageName
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun Context.todayUsageSnapshot(): UsageInsightSnapshot {
    val stats = runCatching {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        ).orEmpty()
    }.getOrDefault(emptyList())
        .filter { it.totalTimeInForeground > 0L && it.packageName != packageName }

    if (stats.isEmpty()) {
        return UsageInsightSnapshot(
            hasUsageAccess = false,
            screenTimeLabel = "Grant access",
            screenTimeTrend = "Tap to enable",
            topAppLabel = "No data",
            topAppTrend = "Usage Access"
        )
    }

    val totalMillis = stats.sumOf { it.totalTimeInForeground }
    val topApp = stats.maxByOrNull { it.totalTimeInForeground }
    return UsageInsightSnapshot(
        hasUsageAccess = true,
        screenTimeLabel = totalMillis.formatDuration(),
        screenTimeTrend = "Live today",
        topAppLabel = topApp?.readableAppName(this) ?: "Unknown",
        topAppTrend = topApp?.totalTimeInForeground?.formatDuration() ?: "0m"
    )
}

private fun UsageStats.readableAppName(context: Context): String {
    val packageManager = context.packageManager
    return runCatching {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    }.getOrDefault(packageName.substringAfterLast('.'))
}

private fun Long.formatDuration(): String {
    val totalMinutes = (this / 60000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun LatestIntentDetection.sourceLabel(): String {
    return when {
        packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
        packageName.contains("gm", ignoreCase = true) -> "Gmail"
        packageName.contains("messaging", ignoreCase = true) -> "Messages"
        packageName.contains("teams", ignoreCase = true) -> "Teams"
        packageName.contains("telegram", ignoreCase = true) -> "Telegram"
        packageName.contains("slack", ignoreCase = true) -> "Slack"
        packageName == "voice" -> "Voice Command"
        packageName == "assistive.ask" -> "Ask iKoo"
        packageName.isBlank() -> "Screen Detection"
        else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}

private fun LatestIntentDetection.liveSummary(): String {
    eventData?.let { event ->
        return listOfNotNull(event.title, event.datePhrase, event.timePhrase)
            .joinToString(" · ")
            .ifBlank { visibleText }
    }
    return visibleText.ifBlank { result.reason }
}

private fun LatestIntentDetection.ageLabel(): String {
    val seconds = ((System.currentTimeMillis() - detectedAtMillis) / 1000L).coerceAtLeast(0L)
    return when {
        seconds < 60 -> "${seconds}s"
        else -> "${seconds / 60}m"
    }
}

private fun IntentType.displayName(): String {
    return when (this) {
        IntentType.CALENDAR_EVENT -> "Event"
        IntentType.REMINDER -> "Reminder"
        IntentType.TASK -> "Task"
        IntentType.NONE -> "Idle"
    }
}

private const val FIVE_MINUTES_MILLIS = 5L * 60L * 1000L

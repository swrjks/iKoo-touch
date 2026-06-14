package com.sudocode.ikoo.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.sudocode.ikoo.R
import com.sudocode.ikoo.accessibility.IKooAccessibilityService
import com.sudocode.ikoo.ui.theme.ElectricMint
import com.sudocode.ikoo.ui.theme.Frost
import com.sudocode.ikoo.ui.theme.MutedFrost
import com.sudocode.ikoo.ui.theme.SignalBlue
import com.sudocode.ikoo.ui.theme.SoftViolet
import kotlinx.coroutines.delay

/**
 * Premium Permission Card - iQOO Gaming Style
 *
 * Enhanced with:
 * - Glass morphism design
 * - Animated status indicators
 * - RGB accent lighting when active
 * - Premium haptic feedback
 * - Dynamic icon animations
 */
@Composable
fun PermissionCard(
    modifier: Modifier = Modifier,
    showDetailedInfo: Boolean = true
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var permissionState by remember { mutableStateOf(context.readAccessibilityState()) }
    val lifecycleOwner = context as? LifecycleOwner
    var isHovered by remember { mutableStateOf(false) }

    // Glow animation for active state
    val glowIntensity by rememberInfiniteTransition().animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowIntensity"
    )

    DisposableEffect(context, lifecycleOwner) {
        permissionState = context.readAccessibilityState()
        if (lifecycleOwner == null) {
            return@DisposableEffect onDispose { }
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = context.readAccessibilityState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    PremiumCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                shadowElevation = if (isHovered) 24f else 12f
                spotShadowColor = if (permissionState.enabled) ElectricMint else SignalBlue
            },
        cornerRadius = 28,
        variant = CardVariant.GAMING,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF071226).copy(alpha = 0.96f),
                            Color(0xFF050913).copy(alpha = 0.98f)
                        )
                    )
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AnimatedPermissionIcon(
                    enabled = permissionState.enabled,
                    modifier = Modifier.size(52.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Accessibility",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 28.sp
                        ),
                        color = Frost,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    AnimatedContent(
                        targetState = permissionState.enabled,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) +
                                    slideInVertically { 20 } togetherWith
                                    fadeOut(animationSpec = tween(200)) +
                                    slideOutVertically { -20 }
                        },
                        label = "permissionSummary"
                    ) { enabled ->
                        Text(
                            text = if (enabled) {
                                "iKoo can read visible text locally and suggest smart calendar actions."
                            } else {
                                "Enable iKoo Screen Intent Monitor to scan visible text from apps."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MutedFrost.copy(alpha = 0.94f),
                            lineHeight = 21.sp
                        )
                    }
                }

                EnhancedStatusPill(
                    enabled = permissionState.enabled,
                    glowIntensity = if (permissionState.enabled) glowIntensity else 0f
                )
            }

            AnimatedBadge(
                text = if (permissionState.enabled) "READY" else "REQUIRED",
                active = !permissionState.enabled
            )

            AnimatedVisibility(
                visible = !permissionState.enabled && showDetailedInfo,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    PermissionBenefitRow(
                        icon = "TXT",
                        text = "Read visible text from any app",
                        color = ElectricMint
                    )
                    PermissionBenefitRow(
                        icon = "AI",
                        text = "Detect calendar events, reminders, and tasks",
                        color = SignalBlue
                    )
                    PermissionBenefitRow(
                        icon = "SEC",
                        text = "All processing happens offline - private by default",
                        color = SoftViolet
                    )
                }
            }

            if (permissionState.enabled && showDetailedInfo) {
                PermissionStatusDetails()
            }

            EnhancedActionButton(
                enabled = permissionState.enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        }
    }
}

/**
 * Animated Permission Icon with state transitions
 */
@Composable
private fun AnimatedPermissionIcon(
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (enabled) 0f else 360f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "iconRotation"
    )

    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "iconScale"
    )

    val backgroundColor = if (enabled) {
        Brush.linearGradient(
            colors = listOf(ElectricMint, SignalBlue)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFF13213A), Color(0xFF080D18))
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = if (enabled) ElectricMint.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(18.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = enabled,
            transitionSpec = {
                fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
            },
            label = "iconContent"
        ) { isEnabled ->
            Text(
                text = if (isEnabled) "✓" else "!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) Color.White else ElectricMint
            )
        }
    }
}

/**
 * Enhanced Status Pill with RGB glow effect
 */
@Composable
private fun EnhancedStatusPill(
    enabled: Boolean,
    glowIntensity: Float = 0f
) {
    val color by animateColorAsState(
        targetValue = if (enabled) ElectricMint else MutedFrost,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (enabled) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pillScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (enabled) {
                    shadowElevation = 8f * glowIntensity
                    spotShadowColor = color
                }
            }
            .clip(RoundedCornerShape(50))
            .background(if (enabled) color.copy(alpha = 0.14f) else SignalBlue.copy(alpha = 0.12f))
            .then(
                Modifier.border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            if (enabled) color.copy(alpha = 0.8f) else SignalBlue.copy(alpha = 0.45f),
                            if (enabled) color.copy(alpha = 0.3f) else ElectricMint.copy(alpha = 0.18f)
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
            )
            .height(38.dp)
            .width(64.dp),
        contentAlignment = Alignment.Center
    ) {
        // RGB glow ring for active state
        if (enabled && glowIntensity > 0.5f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = 0.3f * glowIntensity),
                                Color.Transparent
                            ),
                            radius = 1.5f
                        )
                    )
            )
        }

        AnimatedContent(
            targetState = enabled,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "pillText"
        ) { isEnabled ->
            Text(
                text = if (isEnabled) "ON" else "OFF",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = if (isEnabled) ElectricMint else MutedFrost
            )
        }
    }
}

/**
 * Premium Action Button with hover effects
 */
@Composable
private fun EnhancedActionButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    val buttonGradient = if (enabled) {
        Brush.linearGradient(
            colors = listOf(
                ElectricMint.copy(alpha = 0.95f),
                SignalBlue.copy(alpha = 0.9f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                SignalBlue.copy(alpha = 0.95f),
                ElectricMint.copy(alpha = 0.82f)
            )
        )
    }

    Button(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(28.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(0.dp),
        onClick = {
            isPressed = true
            onClick()
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(buttonGradient)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (enabled) "Review Permission" else "Enable Accessibility",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )

                // Animated arrow
                val arrowOffset by animateFloatAsState(
                    targetValue = if (isPressed) 8f else 0f,
                    animationSpec = spring(),
                    label = "arrowOffset"
                )
                Text(
                    text = "→",
                    fontSize = 16.sp,
                    modifier = Modifier.graphicsLayer { translationX = arrowOffset }
                )
            }
        }
    }
}

/**
 * Permission benefit row with icon animation
 */
@Composable
private fun PermissionBenefitRow(
    icon: String,
    text: String,
    color: Color
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInHorizontally()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f))
                    .border(1.dp, color.copy(alpha = 0.26f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    icon,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = color,
                    maxLines = 1
                )
            }
            Text(
                text = text,
                fontSize = 13.sp,
                color = Frost.copy(alpha = 0.82f),
                modifier = Modifier.weight(1f),
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Detailed permission status with metrics
 */
@Composable
private fun PermissionStatusDetails() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Status cards row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusMetricCard(
                label = "Status",
                value = "Active",
                color = ElectricMint,
                modifier = Modifier.weight(1f)
            )
            StatusMetricCard(
                label = "Mode",
                value = "Offline",
                color = Color(0xFF00E5FF),
                modifier = Modifier.weight(1f)
            )
            StatusMetricCard(
                label = "Privacy",
                value = "Local",
                color = Color(0xFF9B30FF),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Status metric card for details
 */
@Composable
private fun StatusMetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "metricScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = MutedFrost
            )
        }
    }
}

/**
 * Animated badge for feature labels
 */
@Composable
private fun AnimatedBadge(
    text: String,
    active: Boolean
) {
    val pulse by animateFloatAsState(
        targetValue = if (active) 1f else 0.5f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgePulse"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) SignalBlue.copy(alpha = 0.22f * pulse)
                else ElectricMint.copy(alpha = 0.12f)
            )
            .border(
                1.dp,
                if (active) ElectricMint.copy(alpha = 0.28f) else ElectricMint.copy(alpha = 0.18f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) ElectricMint else MutedFrost,
            letterSpacing = 0.5.sp
        )
    }
}

// Preserved original data class and helper function
private data class AccessibilityState(
    val enabled: Boolean,
    val summary: String
)

private fun Context.readAccessibilityState(): AccessibilityState {
    val accessibilityEnabled = Settings.Secure.getInt(
        contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        0
    ) == 1
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    val expectedService = ComponentName(this, IKooAccessibilityService::class.java)
    val serviceEnabled = enabledServices
        .split(':')
        .mapNotNull(ComponentName::unflattenFromString)
        .any { enabledService ->
            enabledService.packageName == expectedService.packageName &&
                    enabledService.className == expectedService.className
        }
    val enabled = accessibilityEnabled && serviceEnabled

    return AccessibilityState(
        enabled = enabled,
        summary = if (enabled) {
            "iKoo is monitoring visible text locally."
        } else {
            "Enable iKoo Screen Intent Monitor to read visible text."
        }
    )
}

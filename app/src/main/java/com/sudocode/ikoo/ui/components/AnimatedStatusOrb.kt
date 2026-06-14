package com.sudocode.ikoo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sudocode.ikoo.ui.theme.ElectricMint
import com.sudocode.ikoo.ui.theme.SignalBlue
import com.sudocode.ikoo.ui.theme.SoftViolet

/**
 * Premium Animated Status Orb - iQOO Gaming Style
 *
 * Retains all original functionality while adding:
 * - Enhanced glow effects
 * - RGB edge lighting (iQOO gaming identity)
 * - Dynamic color transitions
 * - Particle-like arc animations
 * - Shadow depth effects
 */
@Composable
fun AnimatedStatusOrb(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "statusOrbPremium")

    // Original animations preserved
    val pulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )

    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbSweep"
    )

    // NEW: Secondary counter-rotating sweep for premium effect
    val reverseSweep by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbReverseSweep"
    )

    // NEW: Glow intensity animation
    val glowIntensity by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowIntensity"
    )

    // NEW: RGB color cycling for gaming aesthetic (when active)
    val rgbPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rgbPhase"
    )

    // Original colors preserved
    val core = if (active) ElectricMint else SoftViolet
    val accent = if (active) SignalBlue else SoftViolet

    // NEW: RGB gaming colors for outer ring (when active)
    val rgbColors = listOf(
        Color(0xFF00E5FF), // Cyan
        Color(0xFF1478FF), // Electric Blue
        Color(0xFF9B30FF), // Purple
        Color(0xFF2FC7FF), // Cyan glow
        Color(0xFF00E5FF)  // Back to Cyan
    )

    // NEW: Dynamic color for active state with RGB cycling
    val activeCoreColor = if (active) {
        val hue = (rgbPhase + 180) % 360
        Color.hsv(hue, 0.8f, 1f)
    } else {
        core
    }

    Box(
        modifier = modifier
            .size(112.dp)
            .shadow(
                elevation = if (active) 25.dp else 12.dp,
                shape = CircleShape,
                spotColor = if (active) ElectricMint else SoftViolet,
                ambientColor = if (active) SignalBlue else Color.Transparent
            )
    ) {
        Canvas(modifier = Modifier.size(112.dp)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // LAYER 1: Original Radial Gradient (Preserved)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        core.copy(alpha = 0.80f),
                        accent.copy(alpha = 0.38f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * pulse
                ),
                radius = radius * pulse,
                center = center
            )

            // LAYER 2: NEW - RGB Outer Glow Ring (iQOO Gaming Feature)
            if (active) {
                drawCircle(
                    brush = Brush.sweepGradient(colors = rgbColors, center = center),
                    radius = radius + 8.dp.toPx(),
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }

            // LAYER 3: Original Core Gradient (Preserved)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        if (active) activeCoreColor else core,
                        accent,
                        Color.White.copy(alpha = 0.92f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ),
                radius = radius * 0.42f,
                center = center
            )

            // LAYER 4: Original Sweep Arc (Preserved)
            drawArc(
                color = if (active)
                    Color.White.copy(alpha = 0.7f + glowIntensity * 0.3f)
                else
                    Color.White.copy(alpha = 0.54f),
                startAngle = sweep,
                sweepAngle = 96f,
                useCenter = false,
                style = Stroke(
                    width = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // LAYER 5: NEW - Counter-rotating Arc for depth
            drawArc(
                color = if (active)
                    ElectricMint.copy(alpha = 0.6f * glowIntensity)
                else
                    SoftViolet.copy(alpha = 0.3f),
                startAngle = reverseSweep,
                sweepAngle = 48f,
                useCenter = false,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // LAYER 6: NEW - Center Glow Pulse
            if (active) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.8f * glowIntensity),
                            ElectricMint.copy(alpha = 0.4f * glowIntensity),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 0.3f
                    ),
                    radius = radius * (0.25f + glowIntensity * 0.1f),
                    center = center
                )
            }

            // LAYER 7: NEW - Particle dots around the orb (gaming aesthetic)
            if (active) {
                val particleCount = 8
                for (i in 0 until particleCount) {
                    val angle = sweep + (i * 360f / particleCount)
                    val rad = Math.toRadians(angle.toDouble())
                    val x = center.x + (radius + 12.dp.toPx()) * kotlin.math.cos(rad).toFloat()
                    val y = center.y + (radius + 12.dp.toPx()) * kotlin.math.sin(rad).toFloat()
                    drawCircle(
                        color = rgbColors[i % rgbColors.size].copy(alpha = 0.6f),
                        radius = 2.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }

        // NEW: Breathing light overlay
        if (active) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ElectricMint.copy(alpha = 0.15f * glowIntensity),
                                Color.Transparent
                            ),
                            radius = 1.2f
                        )
                    )
            )
        }
    }
}

/**
 * Extended version with additional state for different scenarios
 * Maintains backward compatibility with original function
 */
@Composable
fun AnimatedStatusOrbExtended(
    active: Boolean,
    variant: OrbVariant = OrbVariant.STANDARD,
    modifier: Modifier = Modifier
) {
    when (variant) {
        OrbVariant.STANDARD -> AnimatedStatusOrb(active, modifier)
        OrbVariant.GAMING -> GamingStatusOrb(active, modifier)
        OrbVariant.MINIMAL -> MinimalStatusOrb(active, modifier)
    }
}

enum class OrbVariant {
    STANDARD,  // Original + enhanced
    GAMING,    // Full RGB gaming style
    MINIMAL    // Clean, subtle animation
}

/**
 * Full RGB Gaming variant - Maximum iQOO gaming identity
 */
@Composable
private fun GamingStatusOrb(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "gamingOrb")

    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gamingPulse"
    )

    val hueRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hueRotation"
    )

    val rgbColors = List(5) { i ->
        Color.hsv((hueRotation + i * 72f) % 360f, 1f, 1f)
    }

    Box(
        modifier = modifier
            .size(112.dp)
            .shadow(elevation = 30.dp, shape = CircleShape, spotColor = ElectricMint)
    ) {
        Canvas(modifier = Modifier.size(112.dp)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // RGB rotating ring
            drawCircle(
                brush = Brush.sweepGradient(rgbColors, center = center),
                radius = radius,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Center with pulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, ElectricMint, Color.Transparent),
                    center = center,
                    radius = radius * pulse
                ),
                radius = radius * 0.6f,
                center = center
            )
        }
    }
}

/**
 * Minimal variant - Clean, subtle animation for non-gaming contexts
 */
@Composable
private fun MinimalStatusOrb(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "minimalOrb")

    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "minimalAlpha"
    )

    val core = if (active) ElectricMint else SoftViolet

    Box(
        modifier = modifier
            .size(112.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        core.copy(alpha = alpha),
                        core.copy(alpha = alpha * 0.3f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.size(112.dp)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                color = Color.White.copy(alpha = 0.8f * alpha),
                radius = radius * 0.3f,
                center = center
            )
        }
    }
}

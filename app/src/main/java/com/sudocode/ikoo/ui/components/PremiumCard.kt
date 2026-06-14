package com.sudocode.ikoo.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.sudocode.ikoo.ui.theme.GlassStroke
import com.sudocode.ikoo.ui.theme.ElectricMint
import com.sudocode.ikoo.ui.theme.SignalBlue
import com.sudocode.ikoo.ui.theme.SoftViolet

enum class CardVariant {
    STANDARD,
    GAMING,
    PREMIUM,
    MINIMAL
}

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(22.dp),
    onClick: (() -> Unit)? = null,
    glowEnabled: Boolean = true,
    glowIntensity: Float = 0.6f,
    cornerRadius: Int = 24,
    variant: CardVariant = CardVariant.STANDARD,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "premiumCardScale"
    )
    val shape = RoundedCornerShape(cornerRadius.dp)
    val backgroundBrush = when (variant) {
        CardVariant.STANDARD -> Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.10f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                SignalBlue.copy(alpha = 0.12f)
            )
        )
        CardVariant.GAMING -> Brush.linearGradient(
            listOf(
                ElectricMint.copy(alpha = 0.16f),
                SignalBlue.copy(alpha = 0.12f),
                SoftViolet.copy(alpha = 0.08f)
            )
        )
        CardVariant.PREMIUM -> Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.08f),
                Color.Transparent
            )
        )
        CardVariant.MINIMAL -> Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.07f),
                Color.White.copy(alpha = 0.03f)
            )
        )
    }
    val borderBrush = if (glowEnabled && variant == CardVariant.GAMING) {
        Brush.linearGradient(
            listOf(
                SignalBlue.copy(alpha = glowIntensity),
                ElectricMint.copy(alpha = glowIntensity),
                SoftViolet.copy(alpha = glowIntensity * 0.75f)
            )
        )
    } else {
        Brush.linearGradient(listOf(GlassStroke, GlassStroke))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (variant == CardVariant.MINIMAL) 4f else 12f
                ambientShadowColor = Color.Black.copy(alpha = 0.22f)
                spotShadowColor = Color.Black.copy(alpha = 0.34f)
                this.shape = shape
                clip = false
            }
            .clip(shape)
            .background(backgroundBrush)
            .border(BorderStroke(1.dp, borderBrush), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding)
    ) {
        content()
    }
}

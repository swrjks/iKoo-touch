package com.sudocode.ikoo.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ElectricMint,
    onPrimary = InkBlack,
    secondary = SignalBlue,
    onSecondary = InkBlack,
    tertiary = WarmCoral,
    onTertiary = InkBlack,
    background = InkBlack,
    onBackground = Frost,
    surface = NightSurface,
    onSurface = Frost,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = MutedFrost,
    outline = GlassStroke,
    error = WarmCoral,
    onError = InkBlack
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8A6200),
    onPrimary = Color.White,
    secondary = Color(0xFF005E91),
    onSecondary = Color.White,
    tertiary = Color(0xFFB43500),
    onTertiary = Color.White,
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF151821),
    surface = Color.White,
    onSurface = Color(0xFF151821),
    surfaceVariant = Color(0xFFE7EAF2),
    onSurfaceVariant = Color(0xFF596274),
    outline = Color(0xFFD7DCE8),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val IKooShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun IKooTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = IKooShapes,
        content = content
    )
}

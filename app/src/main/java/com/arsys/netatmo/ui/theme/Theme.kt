package com.arsys.netatmo.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF93CCFF),
    onPrimary = Color(0xFF003351),
    primaryContainer = Color(0xFF3198DC),
    onPrimaryContainer = Color(0xFF002C47),
    secondary = Color(0xFFFFB599),
    onSecondary = Color(0xFF5A1C00),
    secondaryContainer = Color(0xFFF66018),
    onSecondaryContainer = Color(0xFF4F1700),
    tertiary = Color(0xFF62DF7D),
    onTertiary = Color(0xFF003914),
    tertiaryContainer = Color(0xFF1CA64D),
    onTertiaryContainer = Color(0xFF003111),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101419),
    onBackground = Color(0xFFE0E2EA),
    surface = Color(0xFF101419),
    onSurface = Color(0xFFE0E2EA),
    surfaceVariant = Color(0xFF31353B),
    onSurfaceVariant = Color(0xFFBFC7D2),
    surfaceTint = Color(0xFF93CCFF),
    inverseSurface = Color(0xFFE0E2EA),
    inverseOnSurface = Color(0xFF2D3136),
    inversePrimary = Color(0xFF006398),
    outline = Color(0xFF89929B),
    outlineVariant = Color(0xFF3F4850),
    scrim = Color(0xFF000000),
)

@Composable
fun NetatmoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}

val WarmColor = Color(0xFFEA580C)
val CoolColor = Color(0xFF0284C7)
val ComfortColor = Color(0xFF16A34A)
val AwayColor = Color(0xFF64748B)
val FrostColor = Color(0xFF7DD3FC)
val BoilerActiveColor = Color(0xFFF66018)
val SurfaceContainer = Color(0xFF1C2025)
val SurfaceContainerLow = Color(0xFF181C21)
val SurfaceContainerHigh = Color(0xFF262A30)
val SurfaceContainerLowest = Color(0xFF0A0E13)
val SurfaceContainerHighest = Color(0xFF31353B)
val OutlineVariant = Color(0xFF3F4850)

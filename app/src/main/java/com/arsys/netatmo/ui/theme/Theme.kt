package com.arsys.netatmo.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006398),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCEE5FF),
    onPrimaryContainer = Color(0xFF001E31),
    secondary = Color(0xFF8B3A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCC),
    onSecondaryContainer = Color(0xFF311200),
    tertiary = Color(0xFF006D32),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9DF4AA),
    onTertiaryContainer = Color(0xFF00210D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF0F4F8),
    onBackground = Color(0xFF181C22),
    surface = Color(0xFFF0F4F8),
    onSurface = Color(0xFF181C22),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484F),
    surfaceTint = Color(0xFF006398),
    inverseSurface = Color(0xFF2D3136),
    inverseOnSurface = Color(0xFFE0E2EA),
    inversePrimary = Color(0xFF93CCFF),
    outline = Color(0xFF71787F),
    outlineVariant = Color(0xFFC1C8CF),
    scrim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F9FC),
    surfaceContainer = Color(0xFFEDF2F7),
    surfaceContainerHigh = Color(0xFFE6ECF2),
    surfaceContainerHighest = Color(0xFFDFE6ED),
)

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
    surfaceContainerLowest = Color(0xFF0A0E13),
    surfaceContainerLow = Color(0xFF181C21),
    surfaceContainer = Color(0xFF1C2025),
    surfaceContainerHigh = Color(0xFF262A30),
    surfaceContainerHighest = Color(0xFF31353B),
)

@Composable
fun NetatmoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
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

// Roles de superficie/borde que siguen al tema activo (antes eran valores fijos
// oscuros y rompian el tema claro). Mantienen los nombres antiguos para no
// tener que renombrar cientos de usos.
val SurfaceContainer: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer
val SurfaceContainerLow: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
val SurfaceContainerHigh: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
val SurfaceContainerLowest: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
val SurfaceContainerHighest: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
val OutlineVariant: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

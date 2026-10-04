package com.arsys.netatmo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0EA5E9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF38BDF8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBAE6FD),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFF006B54),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF89F8D5),
    onTertiaryContainer = Color(0xFF002117),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF181C1F),
    surface = Color.White,
    onSurface = Color(0xFF181C1F),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF003549),
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF7DD0FF),
    onSecondary = Color(0xFF20333E),
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = Color(0xFF6DDBBA),
    onTertiary = Color(0xFF003829),
    tertiaryContainer = Color(0xFF00513D),
    onTertiaryContainer = Color(0xFF89F8D5),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDEE3E8),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDEE3E8),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC0C8CE)
)

@Composable
fun NetatmoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

val WarmColor = Color(0xFFFF6B35)
val CoolColor = Color(0xFF00B4D8)
val ComfortColor = Color(0xFF4CAF50)
val AwayColor = Color(0xFF9E9E9E)
val FrostColor = Color(0xFF90CAF9)

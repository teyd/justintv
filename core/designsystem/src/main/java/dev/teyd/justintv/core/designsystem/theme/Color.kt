package dev.teyd.justintv.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// The palette keeps Twitch's purple as the brand accent.
internal val Purple = Color(0xFF6441A4)
internal val PurpleLight = Color(0xFFCFBCFF)
internal val PurpleContainerLight = Color(0xFFEADDFF)
internal val PurpleContainerDark = Color(0xFF4F378B)
internal val PurpleOnContainerLight = Color(0xFF21005D)
internal val PurpleOnContainerDark = Color(0xFFEADDFF)

internal val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = PurpleContainerLight,
    onPrimaryContainer = PurpleOnContainerLight,
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

internal val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = PurpleLight,
    onPrimary = Color(0xFF381E72),
    primaryContainer = PurpleContainerDark,
    onPrimaryContainer = PurpleOnContainerDark,
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

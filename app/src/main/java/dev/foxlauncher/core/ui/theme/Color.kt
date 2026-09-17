package dev.foxlauncher.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Fox orange. Used for the launcher icon and as the seed of the static schemes below. */
val FoxOrange = Color(0xFFE8622A)
val FoxOrangeDark = Color(0xFFB3441A)
val FoxCream = Color(0xFFFFF3E4)
val FoxCharcoal = Color(0xFF2B2119)

/** Fallback for devices without dynamic color (API < 31) or when the user turns it off. */
internal val FoxLightColors = lightColorScheme(
    primary = FoxOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCE),
    onPrimaryContainer = Color(0xFF3B0A00),
    secondary = Color(0xFF77574A),
    onSecondary = Color.White,
    background = FoxCream,
    onBackground = FoxCharcoal,
    surface = FoxCream,
    onSurface = FoxCharcoal,
)

internal val FoxDarkColors = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF5C1900),
    primaryContainer = FoxOrangeDark,
    onPrimaryContainer = Color(0xFFFFDBCE),
    secondary = Color(0xFFE7BEAD),
    onSecondary = Color(0xFF442A1F),
    background = Color(0xFF1A110C),
    onBackground = Color(0xFFF1DFD7),
    surface = Color(0xFF1A110C),
    onSurface = Color(0xFFF1DFD7),
)

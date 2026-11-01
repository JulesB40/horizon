package dev.horizonlab.issues.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun HorizonIssuesTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = p.primary,
        onPrimary = androidx.compose.ui.graphics.Color.White,
        background = p.screen,
        onBackground = p.foreground,
        surface = p.screen,
        onSurface = p.foreground,
        surfaceVariant = p.grouped,
        onSurfaceVariant = p.secondary,
        surfaceContainer = p.card,
        surfaceContainerLow = p.card,
        surfaceContainerHigh = p.grouped,
        surfaceContainerHighest = p.grouped,
        outline = p.border,
        outlineVariant = p.separator,
        error = p.danger.content,
        inverseSurface = p.prominent,
        inverseOnSurface = p.onProminent,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

/** The current palette: `Theme.palette.card`. */
object Theme {
    val palette: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

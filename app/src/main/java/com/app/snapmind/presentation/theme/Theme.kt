package com.app.snapmind.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Calm, not grey.
 *
 * The app deliberately avoids badges, counters and streaks (spec.md 7.3), but that is an
 * argument against measuring the user, not against colour. A warm accent and soft surfaces
 * make the thing pleasant to open; nothing here rewards volume or speed.
 */
val LocalSnapMindPalette = staticCompositionLocalOf<SnapMindPalette> { error("No palette provided") }

/** Generously rounded: softer edges read as less demanding than sharp cards. */
private val SnapMindShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private fun colorSchemeFor(p: SnapMindPalette, dark: Boolean): ColorScheme {
    // Same two shades either way, just swapped: light needs dark text on a light chip
    // rather than the light-on-dark pairing dark mode uses.
    val errorContainer = if (dark) WarningContainer else OnWarningContainer
    val onErrorContainer = if (dark) OnWarningContainer else WarningContainer

    return if (dark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = p.background,
            primaryContainer = p.accentMuted,
            onPrimaryContainer = p.onSurface,
            secondary = p.secondary,
            background = p.background,
            onBackground = p.onSurface,
            surface = p.background,
            onSurface = p.onSurface,
            surfaceVariant = p.surfaceRaised,
            onSurfaceVariant = p.onSurfaceFaded,
            surfaceContainer = p.surfaceRaised,
            surfaceContainerHigh = p.surfaceRaisedHigh,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = p.outline
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.background,
            primaryContainer = p.accentMuted,
            onPrimaryContainer = p.onSurface,
            secondary = p.secondary,
            background = p.background,
            onBackground = p.onSurface,
            surface = p.background,
            onSurface = p.onSurface,
            surfaceVariant = p.surfaceRaised,
            onSurfaceVariant = p.onSurfaceFaded,
            surfaceContainer = p.surfaceRaised,
            surfaceContainerHigh = p.surfaceRaisedHigh,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = p.outline
        )
    }
}

@Composable
fun SnapMindTheme(
    palette: PaletteChoice,
    mode: ThemeMode,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val p = paletteFor(palette, dark)

    CompositionLocalProvider(LocalSnapMindPalette provides p) {
        MaterialTheme(
            colorScheme = colorSchemeFor(p, dark),
            shapes = SnapMindShapes,
            content = content
        )
    }
}

package com.inktodo.app.core.ui.theme

import android.animation.ValueAnimator
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalInkPalette = staticCompositionLocalOf { LightInkPalette }
private val LocalInkSpacing = staticCompositionLocalOf { InkSpacing() }
private val LocalInkGlass = staticCompositionLocalOf { InkGlass() }
private val LocalInkMotion = staticCompositionLocalOf { InkMotionSpec(enabled = true) }

object InkTheme {
    val colors: InkPalette
        @Composable get() = LocalInkPalette.current

    val spacing: InkSpacing
        @Composable get() = LocalInkSpacing.current

    val glass: InkGlass
        @Composable get() = LocalInkGlass.current

    val motion: InkMotionSpec
        @Composable get() = LocalInkMotion.current
}

@Composable
fun InkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkInkPalette else LightInkPalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.jade,
            onPrimary = palette.paper,
            secondary = palette.indigo,
            tertiary = palette.cinnabar,
            background = palette.paper,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceStrong,
            onSurfaceVariant = palette.inkSoft,
            outline = palette.glassEdge,
            error = palette.cinnabar,
            onError = palette.surfaceStrong,
            scrim = palette.scrim,
        )
    } else {
        lightColorScheme(
            primary = palette.moss,
            onPrimary = palette.paper,
            secondary = palette.indigo,
            tertiary = palette.cinnabar,
            background = palette.paper,
            onBackground = palette.ink,
            surface = palette.surface,
            onSurface = palette.ink,
            surfaceVariant = palette.surfaceStrong,
            onSurfaceVariant = palette.inkSoft,
            outline = palette.glassEdge,
            error = palette.cinnabar,
            onError = palette.surfaceStrong,
            scrim = palette.scrim,
        )
    }

    CompositionLocalProvider(
        LocalInkPalette provides palette,
        LocalInkSpacing provides InkSpacing(),
        LocalInkGlass provides InkGlass(),
        LocalInkMotion provides InkMotionSpec(enabled = motionEnabled),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = InkTypography,
            shapes = InkShapes,
            content = content,
        )
    }
}

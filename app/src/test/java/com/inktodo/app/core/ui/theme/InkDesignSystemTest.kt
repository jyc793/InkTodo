package com.inktodo.app.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkDesignSystemTest {
    @Test
    fun primaryAndSupportingTextMeetAccessibleContrastInBothThemes() {
        val pairs = listOf(
            ContrastPair("light ink on paper", LightInkPalette.ink, LightInkPalette.paper, 7.0),
            ContrastPair("light soft ink on surface", LightInkPalette.inkSoft, LightInkPalette.surface, 4.5),
            ContrastPair("light mist on strong surface", LightInkPalette.mist, LightInkPalette.surfaceStrong, 4.5),
            ContrastPair("light cinnabar on surface", LightInkPalette.cinnabar, LightInkPalette.surface, 4.5),
            ContrastPair("dark ink on paper", DarkInkPalette.ink, DarkInkPalette.paper, 7.0),
            ContrastPair("dark soft ink on surface", DarkInkPalette.inkSoft, DarkInkPalette.surface, 4.5),
            ContrastPair("dark mist on strong surface", DarkInkPalette.mist, DarkInkPalette.surfaceStrong, 4.5),
            ContrastPair("dark cinnabar on surface", DarkInkPalette.cinnabar, DarkInkPalette.surface, 4.5),
        )

        pairs.forEach { pair ->
            val actual = contrastRatio(pair.foreground, pair.background)
            assertTrue("${pair.name}: $actual < ${pair.minimum}", actual >= pair.minimum)
        }
    }

    @Test
    fun interactionTokensKeepMobileTouchTargetsAtLeastFortyEightDp() {
        val spacing = InkSpacing()

        assertTrue(spacing.touchTarget.value >= 48f)
        assertTrue(spacing.controlHeight.value >= spacing.touchTarget.value)
        assertTrue(spacing.bottomBarHeight.value >= spacing.touchTarget.value)
    }

    @Test
    fun everyProductSceneHasAUniqueMarkAndAccessibleName() {
        assertEquals(InkScene.entries.size, InkScene.entries.map(InkScene::mark).distinct().size)
        assertEquals(InkScene.entries.size, InkScene.entries.map(InkScene::contentName).distinct().size)
        assertTrue(InkScene.entries.all { it.mark.isNotBlank() && it.contentName.isNotBlank() })
    }

    @Test
    fun routineMotionStaysRestrainedAndSlowMotionIsReservedForAtmosphere() {
        assertTrue(InkMotion.PressMillis in 100..200)
        assertTrue(InkMotion.QuickMillis in 100..220)
        assertTrue(InkMotion.StandardMillis in 180..300)
        assertTrue(InkMotion.SlowMillis > InkMotion.StandardMillis)
        assertTrue(InkAtmosphere.NearMistMillis >= 14_000)
        assertTrue(InkAtmosphere.MidMistMillis > InkAtmosphere.NearMistMillis)
        assertTrue(InkAtmosphere.FarMistMillis > InkAtmosphere.MidMistMillis)
        assertTrue(InkAtmosphere.CalendarMoonMillis >= 10_000)
        assertTrue(InkAtmosphere.PomodoroWaterMillis in 4_000..8_000)
        assertTrue(InkAtmosphere.CompletedHazeMillis >= InkAtmosphere.NearMistMillis)
        assertTrue(InkAtmosphere.NearMistDrift.value >= 64f)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
        val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red.toDouble()) +
            0.7152 * linearize(color.green.toDouble()) +
            0.0722 * linearize(color.blue.toDouble())

    private fun linearize(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)

    private data class ContrastPair(
        val name: String,
        val foreground: Color,
        val background: Color,
        val minimum: Double,
    )
}

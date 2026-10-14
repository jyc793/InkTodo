package com.inktodo.app.feature.pomodoro.floating

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingTimerPlacementTest {
    private val bounds = FloatingTimerBounds(minX = 8, maxX = 292, minY = 40, maxY = 680)

    @Test
    fun dragPositionIsClampedInsideSafeBounds() {
        assertEquals(FloatingTimerPoint(8, 680), bounds.clamp(-200, 900))
    }

    @Test
    fun releaseSnapsToNearestHorizontalEdgeWithoutChangingY() {
        assertEquals(FloatingTimerPoint(8, 320), bounds.snapToHorizontalEdge(100, 320))
        assertEquals(FloatingTimerPoint(292, 320), bounds.snapToHorizontalEdge(220, 320))
    }

    @Test
    fun persistedFractionsRoundTripAcrossAResizedDisplay() {
        val fractions = bounds.fractions(FloatingTimerPoint(292, 360))
        val resized = FloatingTimerBounds(minX = 12, maxX = 708, minY = 52, maxY = 1060)

        assertEquals(FloatingTimerPoint(708, 556), resized.fromFractions(fractions.first, fractions.second))
    }

    @Test
    fun visualLayerRemainsCenteredOnTheInteractiveBall() {
        val position = floatingVisualPosition(
            ballPosition = FloatingTimerPoint(292, 360),
            ballSize = 104,
            visualSize = 240,
        )

        assertEquals(FloatingTimerPoint(224, 292), position)
        assertEquals(344, position.x + 240 / 2)
        assertEquals(412, position.y + 240 / 2)
    }
}

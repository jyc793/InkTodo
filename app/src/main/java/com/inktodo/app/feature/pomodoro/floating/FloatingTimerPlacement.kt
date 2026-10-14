package com.inktodo.app.feature.pomodoro.floating

internal data class FloatingTimerPoint(val x: Int, val y: Int)

internal fun floatingVisualPosition(
    ballPosition: FloatingTimerPoint,
    ballSize: Int,
    visualSize: Int,
): FloatingTimerPoint {
    require(ballSize > 0)
    require(visualSize >= ballSize)
    val inset = (visualSize - ballSize) / 2
    return FloatingTimerPoint(
        x = ballPosition.x - inset,
        y = ballPosition.y - inset,
    )
}

internal data class FloatingTimerBounds(
    val minX: Int,
    val maxX: Int,
    val minY: Int,
    val maxY: Int,
) {
    init {
        require(maxX >= minX)
        require(maxY >= minY)
    }

    fun clamp(x: Int, y: Int): FloatingTimerPoint = FloatingTimerPoint(
        x = x.coerceIn(minX, maxX),
        y = y.coerceIn(minY, maxY),
    )

    fun snapToHorizontalEdge(x: Int, y: Int): FloatingTimerPoint {
        val clamped = clamp(x, y)
        val targetX = if (clamped.x - minX <= maxX - clamped.x) minX else maxX
        return clamped.copy(x = targetX)
    }

    fun fromFractions(xFraction: Float, yFraction: Float): FloatingTimerPoint = FloatingTimerPoint(
        x = fractionToCoordinate(xFraction, minX, maxX),
        y = fractionToCoordinate(yFraction, minY, maxY),
    )

    fun fractions(point: FloatingTimerPoint): Pair<Float, Float> {
        val clamped = clamp(point.x, point.y)
        return coordinateToFraction(clamped.x, minX, maxX) to
            coordinateToFraction(clamped.y, minY, maxY)
    }
}

private fun fractionToCoordinate(fraction: Float, min: Int, max: Int): Int =
    (min + (max - min) * fraction.coerceIn(0f, 1f)).toInt()

private fun coordinateToFraction(value: Int, min: Int, max: Int): Float =
    if (max == min) 0f else ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)

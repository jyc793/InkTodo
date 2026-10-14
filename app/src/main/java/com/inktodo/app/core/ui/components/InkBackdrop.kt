package com.inktodo.app.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.inktodo.app.R
import com.inktodo.app.core.ui.theme.InkAtmosphere
import com.inktodo.app.core.ui.theme.InkPalette
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.core.ui.theme.accent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A stable painted landscape with independent, lifecycle-aware atmosphere layers.
 * The artwork never pans: only low-cost cloud and scene-specific layers move.
 */
@Composable
fun InkBackdrop(
    scene: InkScene = InkScene.TODO,
    content: @Composable () -> Unit,
) {
    val colors = InkTheme.colors
    val darkTheme = colors.paper.red < 0.2f
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val animateAtmosphere = InkTheme.motion.enabled && isResumed
    val cloudRibbon = ImageBitmap.imageResource(R.drawable.ink_cloud_ribbon_v1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paperDeep),
    ) {
        Image(
            painter = painterResource(R.drawable.ink_mountain_master_v1),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            alpha = if (darkTheme) 0.55f else 0.62f,
            modifier = Modifier.fillMaxSize(),
        )
        LandscapeVeil(colors = colors, scene = scene)
        FlowingCloudRibbon(
            texture = cloudRibbon,
            depth = MistDepth.FAR,
            scene = scene,
            phase = atmospherePhase(
                enabled = animateAtmosphere,
                durationMillis = InkAtmosphere.FarMistMillis,
                label = "far mist",
            ),
        )
        FlowingCloudRibbon(
            texture = cloudRibbon,
            depth = MistDepth.MID,
            scene = scene,
            phase = atmospherePhase(
                enabled = animateAtmosphere,
                durationMillis = InkAtmosphere.MidMistMillis,
                label = "mid mist",
            ),
        )
        FlowingCloudRibbon(
            texture = cloudRibbon,
            depth = MistDepth.NEAR,
            scene = scene,
            phase = atmospherePhase(
                enabled = animateAtmosphere,
                durationMillis = InkAtmosphere.NearMistMillis,
                label = "near mist",
            ),
        )
        val scenePhase = atmospherePhase(
            enabled = animateAtmosphere && scene.hasAmbientMotion,
            durationMillis = scene.ambientDurationMillis,
            label = "${scene.contentName} atmosphere",
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPaperGrain(colors)
            drawSceneMark(scene = scene, colors = colors, phase = scenePhase)
        }
        content()
    }
}

@Composable
private fun LandscapeVeil(colors: InkPalette, scene: InkScene) {
    val darkTheme = colors.paper.red < 0.2f
    val accent = scene.accent(colors)
    val veil = if (darkTheme) {
        listOf(
            colors.paperDeep.copy(alpha = 0.74f),
            colors.paper.copy(alpha = 0.32f),
            colors.paperDeep.copy(alpha = 0.56f),
        )
    } else {
        listOf(
            colors.paper.copy(alpha = 0.58f),
            colors.paper.copy(alpha = 0.26f),
            colors.paperDeep.copy(alpha = 0.40f),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(veil))
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.055f), accent.copy(alpha = 0f)),
                    center = Offset(0f, 0f),
                    radius = 1_400f,
                ),
            ),
    )
}

@Composable
private fun FlowingCloudRibbon(
    texture: ImageBitmap,
    depth: MistDepth,
    scene: InkScene,
    phase: Float,
) {
    val darkTheme = InkTheme.colors.paper.red < 0.2f
    val widthFactor = when (depth) {
        MistDepth.FAR -> 1.90f
        MistDepth.MID -> 1.66f
        MistDepth.NEAR -> 1.46f
    }
    val verticalAnchor = when (depth) {
        MistDepth.FAR -> 0.32f
        MistDepth.MID -> 0.55f
        MistDepth.NEAR -> 0.80f
    }
    val baseAlpha = when (depth) {
        MistDepth.FAR -> if (darkTheme) 0.20f else 0.27f
        MistDepth.MID -> if (darkTheme) 0.30f else 0.42f
        MistDepth.NEAR -> if (darkTheme) 0.25f else 0.36f
    }
    val sceneAlpha = when (scene) {
        InkScene.TODO -> 1.00f
        InkScene.CALENDAR -> 0.72f
        InkScene.POMODORO -> 0.84f
        InkScene.COMPLETED -> 0.88f
        InkScene.EDITOR -> 0.62f
        InkScene.REMINDER -> 0.70f
        InkScene.SETTINGS -> 0.58f
    }
    val reverseDirection = depth == MistDepth.MID

    Canvas(modifier = Modifier.fillMaxSize()) {
        val tileWidth = (size.width * widthFactor).roundToInt().coerceAtLeast(1)
        val textureRatio = texture.height.toFloat() / texture.width.toFloat()
        val tileHeight = (tileWidth * textureRatio).roundToInt().coerceAtLeast(1)
        val verticalWave = sin(phase * PI * 2.0).toFloat() * size.height * 0.008f
        val tileY = (size.height * verticalAnchor - tileHeight * 0.5f + verticalWave).roundToInt()
        val offset = if (reverseDirection) {
            -tileWidth + phase * tileWidth
        } else {
            -phase * tileWidth
        }
        val firstX = offset.roundToInt()
        val alphaPulse = 0.94f + 0.06f * sin(phase * PI * 2.0).toFloat()
        repeat(2) { copyIndex ->
            drawImage(
                image = texture,
                dstOffset = IntOffset(firstX + copyIndex * tileWidth, tileY),
                dstSize = IntSize(tileWidth, tileHeight),
                alpha = baseAlpha * sceneAlpha * alphaPulse,
                filterQuality = FilterQuality.High,
            )
        }
    }
}

@Composable
private fun atmospherePhase(
    enabled: Boolean,
    durationMillis: Int,
    label: String,
): Float {
    if (!enabled) return 0.5f
    val transition = rememberInfiniteTransition(label = label)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "$label phase",
    )
    return phase
}

private fun DrawScope.drawPaperGrain(colors: InkPalette) {
    repeat(14) { index ->
        val y = size.height * (0.035f + index * 0.073f)
        drawLine(
            color = colors.ink.copy(alpha = if (index % 2 == 0) 0.010f else 0.006f),
            start = Offset(size.width * 0.02f, y),
            end = Offset(size.width * 0.98f, y + size.height * 0.004f),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.drawSceneMark(scene: InkScene, colors: InkPalette, phase: Float) {
    when (scene) {
        InkScene.TODO -> Unit
        InkScene.CALENDAR -> drawMoon(colors, phase)
        InkScene.POMODORO -> Unit
        InkScene.COMPLETED -> drawCompletedHaze(colors, phase)
        InkScene.EDITOR -> drawPaperFibers(colors)
        InkScene.REMINDER -> drawCinnabarSeal(colors, phase)
        InkScene.SETTINGS -> drawStone(colors)
    }
}

private fun DrawScope.drawMoon(colors: InkPalette, phase: Float) {
    val cycle = phase * PI * 2.0
    val pulse = 0.92f + sin(cycle).toFloat() * 0.08f
    val center = Offset(
        size.width * 0.78f + cos(cycle).toFloat() * size.width * 0.006f,
        size.height * 0.18f + sin(cycle).toFloat() * size.height * 0.003f,
    )
    val radius = size.minDimension * 0.15f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.surfaceStrong.copy(alpha = 0.42f * pulse),
                colors.indigo.copy(alpha = 0.08f * pulse),
                colors.indigo.copy(alpha = 0f),
            ),
            center = center,
            radius = radius * 1.75f,
        ),
        radius = radius * 1.75f,
        center = center,
    )
    drawCircle(colors.surfaceStrong.copy(alpha = 0.22f * pulse), radius, center)
    drawCircle(colors.indigo.copy(alpha = 0.20f * pulse), radius, center, style = Stroke(width = 1.3f))
    repeat(2) { index ->
        drawCircle(
            colors.indigo.copy(alpha = (0.075f - index * 0.022f) * pulse),
            radius = radius * (1.22f + index * 0.22f),
            center = center,
            style = Stroke(width = 1f),
        )
    }
}

private fun DrawScope.drawCompletedHaze(colors: InkPalette, phase: Float) {
    val cycle = phase * PI * 2.0
    repeat(3) { index ->
        val angle = cycle + index * PI * 0.72
        val center = Offset(
            x = size.width * (0.28f + index * 0.22f) + cos(angle).toFloat() * size.width * 0.055f,
            y = size.height * (0.54f + (index % 2) * 0.18f) + sin(angle).toFloat() * size.height * 0.018f,
        )
        val radius = size.minDimension * (0.38f + index * 0.10f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.surfaceStrong.copy(alpha = 0.10f),
                    colors.mist.copy(alpha = 0.085f),
                    colors.mist.copy(alpha = 0f),
                ),
                center = center,
                radius = radius,
            ),
            center = center,
            radius = radius,
        )
    }
}

private fun DrawScope.drawPaperFibers(colors: InkPalette) {
    repeat(9) { index ->
        val x = size.width * (0.05f + index * 0.12f)
        drawLine(
            color = colors.moss.copy(alpha = if (index % 2 == 0) 0.032f else 0.018f),
            start = Offset(x, 0f),
            end = Offset(x + size.width * (0.018f + index % 3 * 0.009f), size.height),
            strokeWidth = 1f,
        )
    }
    repeat(11) { index ->
        val y = size.height * (0.06f + index * 0.087f)
        drawLine(
            color = colors.indigo.copy(alpha = if (index % 3 == 0) 0.020f else 0.010f),
            start = Offset(size.width * 0.04f, y),
            end = Offset(size.width * 0.96f, y + size.height * 0.003f),
            strokeWidth = 0.8f,
        )
    }
}

private fun DrawScope.drawCinnabarSeal(colors: InkPalette, phase: Float) {
    val pulse = 0.88f + 0.12f * sin(phase * PI * 2.0).toFloat()
    val center = Offset(size.width * 0.82f, size.height * 0.18f)
    val edge = size.minDimension * 0.18f
    val topLeft = Offset(center.x - edge * 0.5f, center.y - edge * 0.5f)
    drawRoundRect(
        color = colors.cinnabar.copy(alpha = 0.075f * pulse),
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(edge, edge),
        cornerRadius = CornerRadius(edge * 0.12f),
    )
    drawRoundRect(
        color = colors.cinnabar.copy(alpha = 0.26f * pulse),
        topLeft = topLeft,
        size = androidx.compose.ui.geometry.Size(edge, edge),
        cornerRadius = CornerRadius(edge * 0.12f),
        style = Stroke(width = 2f),
    )
}

private fun DrawScope.drawStone(colors: InkPalette) {
    repeat(3) { index ->
        val stoneSize = androidx.compose.ui.geometry.Size(
            width = size.width * (0.30f - index * 0.025f),
            height = size.height * (0.12f + index * 0.014f),
        )
        val topLeft = Offset(
            x = size.width * (0.04f + index * 0.30f),
            y = size.height * (0.68f + (index % 2) * 0.055f),
        )
        val center = Offset(topLeft.x + stoneSize.width * 0.5f, topLeft.y + stoneSize.height * 0.5f)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.surfaceStrong.copy(alpha = 0.08f),
                    colors.inkSoft.copy(alpha = 0.060f + index * 0.012f),
                    colors.ink.copy(alpha = 0.015f),
                ),
                center = Offset(center.x - stoneSize.width * 0.16f, center.y - stoneSize.height * 0.20f),
                radius = stoneSize.width * 0.62f,
            ),
            topLeft = topLeft,
            size = stoneSize,
        )
        drawOval(
            color = colors.inkSoft.copy(alpha = 0.075f),
            topLeft = topLeft,
            size = stoneSize,
            style = Stroke(width = 1f),
        )
    }
}

private enum class MistDepth { FAR, MID, NEAR }

private val InkScene.hasAmbientMotion: Boolean
    get() = when (this) {
        InkScene.CALENDAR, InkScene.COMPLETED, InkScene.REMINDER -> true
        InkScene.TODO, InkScene.POMODORO, InkScene.EDITOR, InkScene.SETTINGS -> false
    }

private val InkScene.ambientDurationMillis: Int
    get() = when (this) {
        InkScene.CALENDAR -> InkAtmosphere.CalendarMoonMillis
        InkScene.POMODORO -> InkAtmosphere.PomodoroWaterMillis
        InkScene.COMPLETED -> InkAtmosphere.CompletedHazeMillis
        InkScene.REMINDER -> InkAtmosphere.CinnabarBreathMillis
        InkScene.TODO, InkScene.EDITOR, InkScene.SETTINGS -> InkAtmosphere.FarMistMillis
    }

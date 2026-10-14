package com.inktodo.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inktodo.app.core.ui.theme.InkScene
import com.inktodo.app.core.ui.theme.InkTheme
import com.inktodo.app.core.ui.theme.accent

@Composable
fun InkSceneHeader(
    title: String,
    scene: InkScene,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = InkTheme.spacing
    val colors = InkTheme.colors
    val accent = scene.accent(colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = spacing.xs, bottom = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(spacing.touchTarget - spacing.sm),
                shape = MaterialTheme.shapes.medium,
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(InkTheme.glass.edgeWidth, accent.copy(alpha = 0.56f)),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(scene.mark, color = accent, style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.ink,
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        color = colors.mist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        action?.invoke()
    }
}

@Composable
fun InkGlassCard(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    muted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = InkTheme.colors
    val glass = InkTheme.glass
    val interaction = remember { MutableInteractionSource() }
    val clickModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .defaultMinSize(minHeight = InkTheme.spacing.touchTarget)
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
    }
    Surface(
        modifier = modifier.then(clickModifier).alpha(if (muted) 0.78f else 1f),
        shape = MaterialTheme.shapes.large,
        color = if (emphasized) {
            colors.glassStrong.copy(alpha = glass.strongCardAlpha)
        } else {
            colors.glass.copy(alpha = if (muted) glass.subtleCardAlpha else glass.cardAlpha)
        },
        border = BorderStroke(
            if (emphasized) glass.strongEdgeWidth else glass.edgeWidth,
            colors.glassEdge,
        ),
        shadowElevation = glass.shadowElevation,
        tonalElevation = glass.tonalLift,
        content = content,
    )
}

@Composable
fun InkPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = InkTheme.spacing.controlHeight),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = InkTheme.colors.moss,
            contentColor = InkTheme.colors.surfaceStrong,
            disabledContainerColor = InkTheme.colors.wash,
            disabledContentColor = InkTheme.colors.mist,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun InkSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = InkTheme.spacing.touchTarget),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(InkTheme.glass.edgeWidth, InkTheme.colors.glassEdge),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = InkTheme.colors.inkSoft,
            disabledContentColor = InkTheme.colors.mist,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** A visually compact action with a full 48dp touch target. */
@Composable
fun InkCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    destructive: Boolean = false,
) {
    val colors = InkTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = InkTheme.spacing.touchTarget,
                minHeight = InkTheme.spacing.touchTarget,
            )
            .semantics { role = Role.Button }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.height(36.dp),
            shape = MaterialTheme.shapes.small,
            color = if (emphasized) colors.moss else colors.glass.copy(alpha = 0.34f),
            border = BorderStroke(
                InkTheme.glass.edgeWidth,
                when {
                    emphasized -> colors.moss
                    destructive -> colors.cinnabar.copy(alpha = 0.62f)
                    else -> colors.glassEdge
                },
            ),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = when {
                        emphasized -> colors.surfaceStrong
                        destructive -> colors.cinnabar
                        else -> colors.inkSoft
                    },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun InkDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = InkTheme.spacing.touchTarget),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(InkTheme.glass.edgeWidth, InkTheme.colors.cinnabar.copy(alpha = 0.56f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = InkTheme.colors.cinnabar),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun InkSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = InkTheme.spacing.sm),
        color = InkTheme.colors.indigo,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun InkStatusSeal(
    text: String,
    scene: InkScene,
    modifier: Modifier = Modifier,
) {
    val accent = scene.accent(InkTheme.colors)
    Surface(
        modifier = modifier.widthIn(min = InkTheme.spacing.touchTarget),
        shape = MaterialTheme.shapes.small,
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(InkTheme.glass.edgeWidth, accent.copy(alpha = 0.52f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = InkTheme.spacing.sm, vertical = InkTheme.spacing.xs),
            color = accent,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

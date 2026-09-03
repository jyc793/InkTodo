package com.inktodo.app.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.inktodo.app.core.ui.theme.InkTheme

@Composable
fun FoundationScreen(
    title: String,
    mark: String,
    description: String,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = InkTheme.spacing
    val colors = InkTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = mark,
                    color = colors.moss,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = title,
                    color = colors.ink,
                    style = MaterialTheme.typography.displaySmall,
                )
            }
            action?.invoke()
        }

        Spacer(modifier = Modifier.height(spacing.xxl))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(InkTheme.glass.edgeWidth, colors.glassEdge, MaterialTheme.shapes.large),
            color = colors.glass.copy(alpha = InkTheme.glass.cardAlpha),
            shape = MaterialTheme.shapes.large,
            tonalElevation = InkTheme.glass.tonalLift,
        ) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(spacing.xxl)
                        .border(InkTheme.glass.edgeWidth, colors.jade, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(mark, color = colors.indigo)
                }
                Spacer(modifier = Modifier.height(spacing.md))
                Text(
                    text = description,
                    color = colors.mist,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

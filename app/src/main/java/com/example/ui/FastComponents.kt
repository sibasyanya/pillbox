package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Обертка над списком, гарантирующая для компилятора Jetpack Compose полную неизменяемость (Stability)
 * и пропуск лишних рекомпозиций при скроллинге LazyColumn.
 */
@Immutable
data class StableList<out T>(val items: List<T>) : List<T> by items

private val DefaultCardShape = RoundedCornerShape(18.dp)
private val DefaultButtonShape = RoundedCornerShape(12.dp)
private val DefaultProgressShape = RoundedCornerShape(6.dp)

/**
 * Облегченная карточка без накладных расходов теневого слоя (RenderNode shadow elevation) и вложенных Surface,
 * обеспечивающая максимальный FPS при быстром скролле списков.
 */
@Composable
fun FastCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
    shape: Shape = DefaultCardShape,
    contentPadding: Dp = 16.dp,
    verticalSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor, shape)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content
    )
}

/**
 * Облегченная кнопка для элементов внутри LazyColumn без многослойного дерева M3 Surface/ProvideTextStyle.
 */
@Composable
fun FastActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    borderColor: Color? = null,
    height: Dp = 48.dp,
    shape: Shape = DefaultButtonShape,
    enabled: Boolean = true,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val effectiveContainer = if (enabled) containerColor else containerColor.copy(alpha = 0.45f)
    val effectiveContent = if (enabled) contentColor else contentColor.copy(alpha = 0.55f)

    val baseModifier = modifier
        .height(height)
        .clip(shape)
        .background(effectiveContainer, shape)
        .let { mod ->
            if (borderColor != null) {
                mod.border(1.dp, borderColor, shape)
            } else {
                mod
            }
        }
        .clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
        .padding(horizontal = 10.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveContent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = fontWeight,
            color = effectiveContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Облегченный индикатор прогресса без покадровой анимации Canvas внутри скроллящихся карточек.
 */
@Composable
fun FastProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 8.dp
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(DefaultProgressShape)
            .background(trackColor, DefaultProgressShape)
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .background(color, DefaultProgressShape)
            )
        }
    }
}

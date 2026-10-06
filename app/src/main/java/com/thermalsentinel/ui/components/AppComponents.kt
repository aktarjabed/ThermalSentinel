package com.thermalsentinel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PreviewBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UI preview mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Screens, navigation and theme controls are active. " +
                        "Monitoring, VPN, scanning and data engines are not connected yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StatusPill(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    val container = if (positive) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer
    val onContainer = if (positive) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onErrorContainer

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = onContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (positive) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun LockedFeatureRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.Lock,
            contentDescription = "Not implemented",
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * `modifier` precedes `content` so callers can use a trailing lambda for the
 * card body: `FeatureCard(title, subtitle, icon) { ... }`. A trailing lambda
 * binds to the LAST parameter, so `content` must be last.
 */
@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DividerRow(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(vertical = 6.dp))
}

@Composable
fun Gauge(
    progress: Float,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp
) {
    // Stroke and inner circle scale proportionally so the gauge renders
    // correctly at compact sizes on 360dp-wide phones.
    val strokeWidth = size * 0.08f
    val indicatorSize = size * 0.88f

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(indicatorSize),
            strokeWidth = strokeWidth,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LineChart(
    points: List<Float>,
    minValue: Float,
    maxValue: Float,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth().height(190.dp)) {
        val strokePx = 3.dp.toPx()
        val dotPx = 4.5.dp.toPx()
        val gridPx = 1.dp.toPx()

        val inset = dotPx
        val plotWidth = size.width - inset * 2f
        val plotHeight = size.height - inset * 2f

        val rows = 4
        repeat(rows + 1) { index ->
            val y = inset + plotHeight * index / rows
            drawLine(
                color = gridColor,
                start = Offset(inset, y),
                end = Offset(inset + plotWidth, y),
                strokeWidth = gridPx
            )
        }

        if (points.size < 2) return@Canvas

        val range = (maxValue - minValue).coerceAtLeast(0.01f)
        fun xAt(index: Int) = inset + plotWidth * index / points.lastIndex
        fun yAt(value: Float) =
            inset + plotHeight * (1f - ((value - minValue) / range).coerceIn(0f, 1f))

        val path = Path()
        points.forEachIndexed { index, value ->
            val x = xAt(index)
            val y = yAt(value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        points.forEachIndexed { index, value ->
            drawCircle(lineColor, radius = dotPx, center = Offset(xAt(index), yAt(value)))
        }
    }
}

@Composable
fun TimelineRow(
    time: String,
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(52.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(bottom = 12.dp)) {
            Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

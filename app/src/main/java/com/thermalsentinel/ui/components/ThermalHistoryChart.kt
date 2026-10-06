package com.thermalsentinel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.thermalsentinel.engine.monitoring.HistoryMath

/**
 * Temperature chart that tells the truth about missing data.
 *
 * Three deliberate differences from a naive sparkline:
 *
 *  1. **x is time, not index.** The horizontal position of a point is its
 *     timestamp, so a period with fewer samples cannot look like a period with
 *     more.
 *  2. **Gaps are gaps.** Segments are split where samples are further apart than
 *     [maxGapMillis], and a point with no measurement breaks the line instead of
 *     being interpolated across.
 *  3. **The threshold is drawn.** The warning threshold the engine actually
 *     alerts on is plotted, so a spike's relationship to the rule is visible
 *     rather than inferred.
 */
@Composable
fun ThermalHistoryChart(
    points: List<HistoryMath.SeriesPoint>,
    minValue: Float,
    maxValue: Float,
    windowStartMillis: Long,
    windowEndMillis: Long,
    thresholdC: Float? = null,
    maxGapMillis: Long = 5 * 60_000L,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val thresholdColor = MaterialTheme.colorScheme.error
    val dotColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxWidth().height(190.dp)) {
        val strokePx = 3.dp.toPx()
        val dotPx = 3.5.dp.toPx()
        val gridPx = 1.dp.toPx()
        val inset = dotPx
        val plotWidth = size.width - inset * 2f
        val plotHeight = size.height - inset * 2f
        val range = (maxValue - minValue).coerceAtLeast(0.01f)
        val windowDuration = (windowEndMillis - windowStartMillis).coerceAtLeast(1L)

        repeat(5) { index ->
            val y = inset + plotHeight * index / 4f
            drawLine(
                color = gridColor,
                start = Offset(inset, y),
                end = Offset(inset + plotWidth, y),
                strokeWidth = gridPx
            )
        }

        fun xAt(timestampMillis: Long): Float {
            val fraction = ((timestampMillis - windowStartMillis).toFloat() / windowDuration.toFloat())
                .coerceIn(0f, 1f)
            return inset + plotWidth * fraction
        }

        fun yAt(value: Float): Float =
            inset + plotHeight * (1f - ((value - minValue) / range).coerceIn(0f, 1f))

        if (thresholdC != null && thresholdC in minValue..maxValue) {
            val y = yAt(thresholdC)
            drawLine(
                color = thresholdColor,
                start = Offset(inset, y),
                end = Offset(inset + plotWidth, y),
                strokeWidth = gridPx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
            )
        }

        if (points.size < 2) return@Canvas

        val segments = HistoryMath.segments(points, maxGapMillis)
        for (segment in segments) {
            // A path is rebuilt after every hole, so no line is ever drawn
            // between two measurements that are not adjacent in time.
            var path: Path? = null
            for (point in segment.points) {
                val value = point.temperatureC
                if (value == null) {
                    path = null
                    continue
                }
                val x = xAt(point.timestampMillis)
                val y = yAt(value)
                val current = path
                if (current == null) {
                    val fresh = Path()
                    fresh.moveTo(x, y)
                    path = fresh
                } else {
                    current.lineTo(x, y)
                }
            }
            path?.let {
                drawPath(
                    path = it,
                    color = lineColor,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // Dots last so they sit on top of the lines.
        for (point in points) {
            val value = point.temperatureC ?: continue
            drawCircle(
                color = dotColor,
                radius = dotPx,
                center = Offset(xAt(point.timestampMillis), yAt(value))
            )
        }
    }
}

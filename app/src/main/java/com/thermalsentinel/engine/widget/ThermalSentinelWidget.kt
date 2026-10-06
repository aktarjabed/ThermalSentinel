package com.thermalsentinel.engine.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
// The day/night factory lives in androidx.glance.color; androidx.glance.unit only
// declares the ColorProvider interface that TextStyle accepts.
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.thermalsentinel.R
import com.thermalsentinel.ThermalSentinelApp
import com.thermalsentinel.engine.domain.DeviceSample
import com.thermalsentinel.engine.domain.ThermalFormatting
import com.thermalsentinel.engine.service.MonitoringStatus
import com.thermalsentinel.ui.MainActivity
import com.thermalsentinel.ui.data.UiPreferencesRepository
import com.thermalsentinel.ui.data.WidgetStyle
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget.
 *
 * Freshness policy: the widget is **pushed** by the monitoring engine when a
 * sample is stored (throttled to once a minute by [WidgetUpdater]), with a
 * one-hour periodic refresh as a fallback. It never polls in the background
 * looking for a nicer number.
 *
 * Honesty policy: with no measurement it says so; with monitoring stopped it
 * shows the last stored reading *and* says that monitoring is off, with the time
 * of that reading. A widget that shows a stale temperature without saying so
 * would be worse than one that shows nothing.
 */
class ThermalSentinelWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = (context.applicationContext as ThermalSentinelApp).graph
        val sample = graph.history.latestSample.value ?: graph.history.loadLatest()
        val monitoringRunning = MonitoringStatus.isRunning.value
        val style = runCatching {
            UiPreferencesRepository(context).preferences.first().widgetStyle
        }.getOrDefault(WidgetStyle.SYSTEM)

        provideContent {
            WidgetContent(
                sample = sample,
                monitoringRunning = monitoringRunning,
                style = style,
                temperatureUnavailable = context.getString(R.string.widget_temperature_unavailable),
                noData = context.getString(R.string.widget_state_no_data),
                monitoringOff = context.getString(R.string.widget_state_monitoring_off)
            )
        }
    }
}

@Composable
private fun WidgetContent(
    sample: DeviceSample?,
    monitoringRunning: Boolean,
    style: WidgetStyle,
    temperatureUnavailable: String,
    noData: String,
    monitoringOff: String
) {
    // Glance composables have no activity context of their own; LocalContext is the
    // one the widget is being rendered for, which is what the tap intent needs.
    val context = LocalContext.current
    val background = when (style) {
        WidgetStyle.TRANSPARENT -> ColorProvider(day = Color.Transparent, night = Color.Transparent)
        WidgetStyle.THEMED -> ColorProvider(day = Color(0xFFE8F0FE), night = Color(0xFF1F2A44))
        WidgetStyle.SYSTEM -> ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF121212))
    }
    val primaryText = when (style) {
        WidgetStyle.THEMED -> ColorProvider(day = Color(0xFF10203F), night = Color(0xFFD7E4FF))
        else -> ColorProvider(day = Color(0xFF111111), night = Color(0xFFFFFFFF))
    }
    val secondaryText = when (style) {
        WidgetStyle.THEMED -> ColorProvider(day = Color(0xFF33456B), night = Color(0xFFAFC4EA))
        else -> ColorProvider(day = Color(0xFF5F6368), night = Color(0xFFB0B0B0))
    }

    val temperature = sample?.battery?.temperatureC?.valueOrNull
    val status = sample?.thermal?.thermalStatus?.valueOrNull
    val percent = sample?.battery?.batteryPercent?.valueOrNull
    val headroom = sample?.thermal?.headroom?.valueOrNull

    val detailLine = when {
        sample == null -> noData
        monitoringRunning -> buildString {
            append(if (headroom == null) "headroom unavailable" else "headroom ${ThermalFormatting.headroom(headroom)}")
            append(" · ").append(ThermalFormatting.clock(sample.timestampMillis))
        }
        else -> "$monitoringOff ${ThermalFormatting.clock(sample.timestampMillis)}"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.Vertical.Top,
        horizontalAlignment = Alignment.Horizontal.Start
    ) {
        Text(
            text = if (monitoringRunning) "Monitoring active" else "Monitoring off",
            style = TextStyle(color = secondaryText, fontSize = 11.sp)
        )
        Text(
            text = temperature?.let { ThermalFormatting.temperatureCelsius(it) } ?: temperatureUnavailable,
            style = TextStyle(color = primaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        )
        Text(
            text = buildString {
                // A missing platform status is written as "unknown", never as "normal".
                append(status?.label ?: "status unknown")
                if (percent != null) append(" · ").append(percent).append("%")
            },
            style = TextStyle(color = primaryText, fontSize = 14.sp)
        )
        Text(
            text = detailLine,
            style = TextStyle(color = secondaryText, fontSize = 12.sp)
        )
    }
}

class ThermalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ThermalSentinelWidget()
}

/**
 * Pushes widget refreshes from the engine, throttled to one refresh per minute.
 * Sampling can run as fast as every five seconds during an event, and rebuilding
 * a widget that often would cost more battery than the monitoring itself.
 */
object WidgetUpdater {

    const val MIN_UPDATE_INTERVAL_MILLIS = 60_000L

    /**
     * Null means "has never updated", which is not the same statement as "updated
     * at time zero" — a zero here would silently swallow the first update after a
     * boot that happened within the throttle window.
     */
    @Volatile
    private var lastUpdateAtElapsedRealtime: Long? = null

    /**
     * Throttled on `elapsedRealtime`, not the wall clock. Setting the device clock
     * backwards must not freeze widget updates until the clock catches up; the
     * headroom limiter makes the same choice for the same reason, and a throttle
     * built on a clock the user controls is not a throttle.
     */
    suspend fun onSampleStored(context: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        val previous = lastUpdateAtElapsedRealtime
        if (previous != null && now - previous < MIN_UPDATE_INTERVAL_MILLIS) return
        lastUpdateAtElapsedRealtime = now
        runCatching { ThermalSentinelWidget().updateAll(context) }
    }
}

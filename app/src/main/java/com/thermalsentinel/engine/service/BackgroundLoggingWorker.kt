package com.thermalsentinel.engine.service

import android.content.Context
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.thermalsentinel.ThermalSentinelApp
import com.thermalsentinel.engine.collector.BatteryCollector
import com.thermalsentinel.engine.domain.AbsenceReason
import com.thermalsentinel.engine.domain.PlatformValues
import com.thermalsentinel.engine.domain.Reading
import com.thermalsentinel.engine.domain.SampleReason
import com.thermalsentinel.engine.domain.ThermalSnapshot
import com.thermalsentinel.engine.monitoring.BackgroundLoggingPolicy
import com.thermalsentinel.engine.monitoring.SampleAssembler
import com.thermalsentinel.engine.monitoring.SampleRequest
import com.thermalsentinel.engine.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException

/**
 * Opt-in, low-frequency history fallback. This worker never launches the foreground
 * monitoring service, never publishes alerts, and never reads thermal headroom.
 */
class BackgroundLoggingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ThermalSentinelApp ?: return Result.failure()
        val graph = app.graph
        var batteryCollector: BatteryCollector? = null
        try {
            if (!graph.settings.backgroundLoggingEnabledNow()) return Result.success()
            val now = System.currentTimeMillis()
            val latest = graph.history.loadLatest()
            if (!BackgroundLoggingPolicy.shouldSample(
                    monitoringRunning = MonitoringStatus.isRunning.value,
                    latestSampleMillis = latest?.timestampMillis,
                    nowMillis = now
                )
            ) return Result.success()

            val collector = BatteryCollector(applicationContext)
            batteryCollector = collector
            collector.start()
            val powerManager = applicationContext.getSystemService(PowerManager::class.java)
            val thermalStatus = try {
                powerManager?.currentThermalStatus
            } catch (failure: RuntimeException) {
                null
            }
            val thermal = ThermalSnapshot(
                thermalStatus = if (powerManager == null) {
                    Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE)
                } else {
                    PlatformValues.thermalStatusReading(thermalStatus)
                },
                headroom = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET)
            )
            val sample = SampleAssembler.assemble(
                request = SampleRequest(
                    nowMillis = now,
                    reason = SampleReason.BACKGROUND_LOG,
                    requestedIntervalMillis = BackgroundLoggingPolicy.PERIODIC_INTERVAL_MILLIS,
                    previousTimestampMillis = latest?.timestampMillis,
                    screenInteractive = powerManager?.isInteractive ?: false
                ),
                battery = collector.snapshot(now),
                thermal = thermal
            )
            graph.history.record(sample)
            runCatching { WidgetUpdater.onSampleStored(applicationContext) }
            return Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return Result.retry()
        } finally {
            runCatching { batteryCollector?.stop() }
        }
    }
}

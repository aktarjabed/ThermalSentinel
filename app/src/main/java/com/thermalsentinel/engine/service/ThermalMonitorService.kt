package com.thermalsentinel.engine.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.thermalsentinel.ThermalSentinelApp
import com.thermalsentinel.engine.AppGraph
import com.thermalsentinel.engine.alert.AlertLevel
import com.thermalsentinel.engine.alert.AlertNotificationPublisher
import com.thermalsentinel.engine.collector.BatteryCollector
import com.thermalsentinel.engine.data.MonitoringStopReason
import com.thermalsentinel.engine.domain.ThermalEventType
import com.thermalsentinel.engine.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The continuous monitoring foreground service.
 *
 * Three platform contracts shape this file:
 *
 *  - **Type `specialUse`** (declared in the manifest with its required subtype
 *    property). This is user-requested local battery/thermal sensor monitoring,
 *    not data transfer/import/export (`dataSync`) or a health/fitness sensor
 *    session. Android 15+ caps `dataSync` at six hours per 24 h for apps targeting
 *    API 35+, so it cannot represent the continuous monitoring contract. Play
 *    review still requires a use-case justification for `specialUse`.
 *  - **Foreground within five seconds.** [startAsForeground] runs before any
 *    suspending work, using whatever sample is already cached.
 *  - **`START_NOT_STICKY`.** A sticky restart can be rejected on API 31+ and
 *    risks a crash loop; a silent restart would also make the recorded stop
 *    reason untrue.
 *
 * Monitoring is only ever started from a visible screen (see
 * `MonitoringController`), never from `Application.onCreate`.
 */
class ThermalMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var graph: AppGraph
    private var coordinator: MonitoringCoordinator? = null

    override fun onCreate() {
        super.onCreate()
        graph = (application as ThermalSentinelApp).graph
        graph.notifications.ensureChannels()
        MonitoringStatus.setRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        if (coordinator == null) {
            val created = buildCoordinator()
            coordinator = created
            serviceScope.launch {
                val now = System.currentTimeMillis()
                // Session facts only. The monitoring *intent* was already persisted
                // by MonitoringController.start() before this service existed, so a
                // stop issued while this coroutine was queued cannot be undone by a
                // late start write landing after it.
                graph.settings.recordSessionStarted(now)
                graph.history.recordEvent(
                    timestampMillis = now,
                    type = ThermalEventType.MONITORING_STARTED,
                    detail = "Monitoring started from the app."
                )
                created.start()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val active = coordinator
        coordinator = null
        active?.stop()
        MonitoringStatus.setRunning(false)

        // The final write runs on the application scope, which outlives this
        // service, so the reason for stopping is not lost while the process is
        // being torn down. The persisted intent is still "on" only when the stop
        // was *not* user-initiated — `MonitoringController.stop()` clears it
        // first — so an OEM kill is recorded as such and shown on the Dashboard.
        graph.appScope.launch {
            val now = System.currentTimeMillis()
            if (graph.settings.monitoringEnabledNow()) {
                graph.settings.recordMonitoringStopped(now, MonitoringStopReason.PLATFORM_STOPPED)
                graph.history.recordEvent(
                    timestampMillis = now,
                    type = ThermalEventType.MONITORING_STOPPED,
                    detail = "Android stopped the monitoring service. Monitoring is paused until it is resumed."
                )
            }
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val sample = graph.history.latestSample.value
        // Android requires startForeground within five seconds of the service being
        // started, so this notification is built from cached state and with no
        // alert annotation at all — it claims nothing about the alert level. The
        // coordinator refreshes it with the persisted level as soon as that state
        // has been read back, which is the first thing its loop does.
        val notification = graph.notifications.buildMonitoringNotification(
            sample = sample,
            alertLevel = AlertLevel.NORMAL,
            updating = false
        )
        // API 34+ requires the type to be passed and to match the manifest.
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            AlertNotificationPublisher.ID_MONITORING,
            notification,
            foregroundServiceType
        )
    }

    private fun buildCoordinator(): MonitoringCoordinator {
        val interactiveProvider: () -> Boolean = {
            getSystemService(PowerManager::class.java)?.isInteractive ?: true
        }
        return MonitoringCoordinator(
            settings = graph.settings,
            history = graph.history,
            batteryCollector = BatteryCollector(applicationContext),
            thermalCollector = graph.thermalCollector,
            publisher = graph.notifications,
            interactiveProvider = interactiveProvider,
            onSampleStored = { WidgetUpdater.onSampleStored(applicationContext) },
            scope = serviceScope
        )
    }

    companion object {
        const val ACTION_START = "com.thermalsentinel.engine.action.START_MONITORING"
        const val ACTION_STOP = "com.thermalsentinel.engine.action.STOP_MONITORING"
    }
}

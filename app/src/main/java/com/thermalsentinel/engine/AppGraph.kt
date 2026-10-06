package com.thermalsentinel.engine

import android.content.Context
import com.thermalsentinel.engine.alert.AlertNotificationPublisher
import com.thermalsentinel.engine.collector.ThermalCollector
import com.thermalsentinel.engine.data.EngineDatabase
import com.thermalsentinel.engine.data.EngineSettingsRepository
import com.thermalsentinel.engine.data.ThermalHistoryRepository
import com.thermalsentinel.engine.diagnostics.DiagnosticsEngine
import com.thermalsentinel.engine.export.ThermalExportService
import com.thermalsentinel.engine.service.MonitoringController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency graph.
 *
 * A DI framework would add a build-time processor, a second source of truth about
 * wiring, and a test story no better than constructor injection for a graph this
 * size. Everything here is process-scoped and lazily created, which matters: the
 * widget, the monitoring service and the UI share exactly one history repository,
 * and that is what keeps their numbers identical.
 */
class AppGraph(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * Scope for work that must outlive the monitoring service — most importantly
     * the "monitoring stopped" bookkeeping written from `Service.onDestroy`.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: EngineDatabase by lazy { EngineDatabase.get(appContext) }

    val settings: EngineSettingsRepository by lazy { EngineSettingsRepository(appContext) }

    val history: ThermalHistoryRepository by lazy { ThermalHistoryRepository(database) }

    val notifications: AlertNotificationPublisher by lazy { AlertNotificationPublisher(appContext) }

    /** One process-scoped collector so service and diagnostics share API throttling. */
    val thermalCollector: ThermalCollector by lazy { ThermalCollector(appContext) }

    val monitoring: MonitoringController by lazy { MonitoringController(appContext, settings) }

    val exporter: ThermalExportService by lazy { ThermalExportService(appContext, history) }

    val diagnostics: DiagnosticsEngine by lazy {
        DiagnosticsEngine(appContext, history, settings, notifications, thermalCollector)
    }

    /**
     * Called once from `Application.onCreate`. Creates the notification channels,
     * warms the cached latest sample so the first frame and the ongoing
     * notification are not empty, and runs the retention ladder if it is due.
     *
     * It deliberately does **not** start monitoring: a foreground-service start
     * from `Application.onCreate` is a background start whenever the process was
     * created by a widget update or a broadcast, and Android refuses those.
     */
    fun bootstrap() {
        appScope.launch {
            notifications.ensureChannels()
            history.loadLatest()
            history.runMaintenanceIfDue()
        }
    }
}

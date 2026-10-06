package com.thermalsentinel

import android.app.Application
import com.thermalsentinel.engine.AppGraph

/**
 * Application entry point.
 *
 * Deliberately does **not** start monitoring. Starting a foreground service from
 * `Application.onCreate` would be a background start whenever the process was
 * created by anything other than a user action (a widget update, a broadcast),
 * and Android rejects those on API 31+. Monitoring resumes from a screen the user
 * is actually looking at — see `MonitoringController.resumeIfRequested`.
 */
class ThermalSentinelApp : Application() {

    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        graph.bootstrap()
    }
}

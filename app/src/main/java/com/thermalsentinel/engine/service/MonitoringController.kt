package com.thermalsentinel.engine.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.thermalsentinel.engine.data.EngineSettingsRepository
import com.thermalsentinel.engine.data.MonitoringStopReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide visibility of whether the monitoring service is currently alive.
 *
 * The service runs in the app process, so a plain in-memory flow is the honest
 * source of truth for "is this running right now". The persisted
 * `monitoring_enabled` flag answers a different question — "does the user want
 * monitoring" — and the UI shows both, because an OEM battery manager killing
 * the service must not look like the user's own choice.
 */
object MonitoringStatus {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    internal fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
}

/** Result of asking the platform to start monitoring. */
sealed interface MonitoringStartResult {
    data object Started : MonitoringStartResult

    /** The platform refused to start a foreground service from this context. */
    data class Rejected(val message: String) : MonitoringStartResult
}

/**
 * The only place that starts and stops monitoring.
 *
 * Starts happen while the user is looking at the app (the Settings or Dashboard
 * toggle), which is what makes a foreground-service start legal without any
 * background-start exemption. Every rejection path is returned to the caller so
 * the UI can say "Android refused to start monitoring right now" instead of
 * showing a switch that claims to be on.
 *
 * **This class is the only writer of the persisted monitoring intent.** That is a
 * lifecycle decision, not a convenience: the service records its own session
 * bookkeeping, but if it also wrote `monitoring_enabled`, a stop issued while the
 * service's start write was still queued could be followed by that write and leave
 * the intent "on" with nothing running — after which the next launch would
 * silently restart monitoring the user had just turned off. Writing the intent on
 * both the start and the stop path here keeps the two in one call stack, in the
 * order the user produced them.
 */
class MonitoringController(
    private val context: Context,
    private val settings: EngineSettingsRepository
) {
    val isRunning: StateFlow<Boolean> = MonitoringStatus.isRunning

    suspend fun start(): MonitoringStartResult {
        // Recorded before the service is asked to start, so a stop that arrives
        // while the start is in flight can never be undone by it.
        settings.setMonitoringEnabled(true)
        val intent = Intent(context, ThermalMonitorService::class.java)
            .setAction(ThermalMonitorService.ACTION_START)
        return try {
            ContextCompat.startForegroundService(context, intent)
            MonitoringStartResult.Started
        } catch (notAllowed: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Android refused a background foreground-service start", notAllowed)
            abandonStart("Android did not allow monitoring to start in the background. Open the app and try again.")
        } catch (illegalState: IllegalStateException) {
            Log.w(TAG, "Foreground service start failed", illegalState)
            abandonStart("Monitoring could not start: ${illegalState.message ?: "unknown reason"}")
        } catch (security: SecurityException) {
            Log.w(TAG, "Foreground service start was denied", security)
            abandonStart("Monitoring could not start: the foreground-service permission was denied.")
        } catch (unexpected: RuntimeException) {
            // Fail-safe, and deliberately last: an OEM ROM throwing anything else
            // from a service start must still be reported as a refused start. A
            // crash here would also leave the persisted intent switched on with no
            // service behind it.
            Log.w(TAG, "Foreground service start threw an unexpected error", unexpected)
            abandonStart("Monitoring could not start: ${unexpected.message ?: "unexpected platform error"}")
        }
    }

    /**
     * A refused start must not leave an intent to resume behind, or the next
     * launch would try to start a service the platform has already declined. If a
     * service is nonetheless running, the intent stays as it is: this method rolls
     * back *its own* start, it does not switch a live session off.
     */
    private suspend fun abandonStart(message: String): MonitoringStartResult {
        if (!MonitoringStatus.isRunning.value) settings.setMonitoringEnabled(false)
        return MonitoringStartResult.Rejected(message)
    }

    /**
     * User-initiated stop. The intent flag is recorded here rather than in the
     * service, so the service can distinguish "the user turned this off" from
     * "Android killed us" when it writes the stop reason. The write is awaited
     * before `stopService`: `Service.onDestroy` reads this same flag to decide
     * whether to record a platform stop, and DataStore serves that read from the
     * value its transaction just committed, so the ordering is guaranteed rather
     * than racy.
     */
    suspend fun stop() {
        settings.recordMonitoringStopped(System.currentTimeMillis(), MonitoringStopReason.USER_REQUEST)
        context.stopService(Intent(context, ThermalMonitorService::class.java))
    }

    /**
     * Called from a foreground screen on launch. Resuming only happens when the
     * user previously asked for monitoring, so the app never decides on its own
     * to start a persistent service.
     */
    suspend fun resumeIfRequested(): MonitoringStartResult? {
        if (MonitoringStatus.isRunning.value) return null
        if (!settings.monitoringEnabledNow()) return null
        return start()
    }

    companion object {
        private const val TAG = "MonitoringController"
    }
}

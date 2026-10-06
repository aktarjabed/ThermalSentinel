package com.thermalsentinel.engine.collector

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.thermalsentinel.engine.domain.AbsenceReason
import com.thermalsentinel.engine.domain.PlatformValues
import com.thermalsentinel.engine.domain.Reading
import com.thermalsentinel.engine.domain.ThermalSnapshot
import com.thermalsentinel.engine.domain.ThermalStatusBand
import com.thermalsentinel.engine.monitoring.ThermalHeadroomReadLimiter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform thermal state: `PowerManager.getCurrentThermalStatus()` with a status
 * listener, plus `getThermalHeadroom(forecastSeconds)`.
 *
 * Headroom is read no more often than once every ten seconds, in line with the
 * Android thermal API guidance. The collector is process-scoped and shared with
 * Diagnostics, and the read itself is synchronized, so neither sampling nor a
 * diagnostics refresh can bypass the platform limit by polling on another thread.
 * NaN remains an unavailable reading; it is never converted to zero.
 *
 * Headroom is a dimensionless ratio (1.0 = the SEVERE threshold, values above
 * 1.0 are possible). It is never labelled a temperature.
 */
class ThermalCollector(private val context: Context) {

    private val powerManager: PowerManager? = context.getSystemService(PowerManager::class.java)

    private val _thermalStatus = MutableStateFlow<Reading<ThermalStatusBand>>(
        Reading.Absent(AbsenceReason.NOT_COLLECTED_YET)
    )
    val thermalStatus: StateFlow<Reading<ThermalStatusBand>> = _thermalStatus.asStateFlow()

    private val listener = PowerManager.OnThermalStatusChangedListener { statusLevel ->
        _thermalStatus.value = Reading.Present(ThermalStatusBand.fromAndroidLevel(statusLevel))
    }

    private var registered = false
    private val headroomReadLimiter = ThermalHeadroomReadLimiter(HEADROOM_MIN_INTERVAL_MILLIS)
    private var lastHeadroomReading: Reading<Float> = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET)

    val isPowerManagerAvailable: Boolean get() = powerManager != null

    fun start() {
        val manager = powerManager ?: run {
            _thermalStatus.value = Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE)
            return
        }
        if (!registered) {
            manager.addThermalStatusListener(ContextCompat.getMainExecutor(context), listener)
            registered = true
        }
        _thermalStatus.value = Reading.Present(
            ThermalStatusBand.fromAndroidLevel(manager.currentThermalStatus)
        )
    }

    /**
     * Stops listening and **retires the last status**. The collector is
     * process-scoped and shared with Diagnostics, so a value left behind here
     * outlives the session that produced it: the next reader would show a status
     * from a stopped session as though the platform had just reported it. Absence
     * with a reason is the honest replacement.
     *
     * The headroom cache is deliberately *not* cleared. It is a throttle cache,
     * Diagnostics labels it as the "most recent reading", and clearing it would
     * only cost the next reader its 10-second platform budget.
     */
    fun stop() {
        if (!registered) return
        runCatching { powerManager?.removeThermalStatusListener(listener) }
        registered = false
        _thermalStatus.value = Reading.Absent(AbsenceReason.SAMPLING_STOPPED)
    }

    /**
     * Most recent headroom reading, refreshed only when the shared 10-second
     * throttle allows it. Elapsed realtime is used rather than wall-clock time:
     * changing the device clock must not accidentally increase the platform poll
     * rate.
     */
    @Synchronized
    fun headroom(): Reading<Float> {
        val manager = powerManager
        if (manager == null) {
            lastHeadroomReading = Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE)
            return lastHeadroomReading
        }
        if (!headroomReadLimiter.tryAcquire(SystemClock.elapsedRealtime())) return lastHeadroomReading

        val raw = runCatching { manager.getThermalHeadroom(FORECAST_SECONDS) }.getOrNull()
        lastHeadroomReading = PlatformValues.headroomReading(raw)
        return lastHeadroomReading
    }

    fun snapshot(): ThermalSnapshot = ThermalSnapshot(
        thermalStatus = _thermalStatus.value,
        headroom = headroom()
    )

    companion object {
        /** Android's recommended minimum interval between headroom API calls. */
        const val HEADROOM_MIN_INTERVAL_MILLIS =
            ThermalHeadroomReadLimiter.DEFAULT_MINIMUM_INTERVAL_MILLIS

        /** Forecast horizon used for each API read. */
        const val FORECAST_SECONDS = 10

        /**
         * Whether the running platform exposes the headroom API at all. Reported
         * separately from a single NaN result so Diagnostics can distinguish
         * "this Android version cannot do it" from "this device cannot do it".
         */
        val isHeadroomApiAvailable: Boolean
            get() = android.os.Build.VERSION.SDK_INT >= 30
    }
}

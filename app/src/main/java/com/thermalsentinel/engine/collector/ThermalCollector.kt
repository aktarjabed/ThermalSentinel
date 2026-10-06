package com.thermalsentinel.engine.collector

import android.content.Context
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.thermalsentinel.engine.domain.AbsenceReason
import com.thermalsentinel.engine.domain.PlatformValues
import com.thermalsentinel.engine.domain.Reading
import com.thermalsentinel.engine.domain.ThermalSnapshot
import com.thermalsentinel.engine.domain.ThermalStatusBand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform thermal state: `PowerManager.getCurrentThermalStatus()` with a status
 * listener, plus `getThermalHeadroom(forecastSeconds)`.
 *
 * Why headroom is read at most once per second:
 * the platform documents that calling it much more often than about once per
 * second "may result in the function returning NaN" — and NaN is also the
 * documented "not supported" answer. Caching for a second keeps those two cases
 * distinguishable instead of turning a self-inflicted NaN into a false claim
 * that the device lacks the API.
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
    private var lastHeadroomReadAtMillis = 0L
    private var lastHeadroomReading: Reading<Float> = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET)

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

    fun stop() {
        if (!registered) return
        runCatching { powerManager?.removeThermalStatusListener(listener) }
        registered = false
    }

    /**
     * Current headroom, cached for [HEADROOM_CACHE_MILLIS]. `forecastSeconds` of
     * 10 is the recommendation from the platform's own performance guidance: it
     * is a forecast of how close the device is to throttling, not an instant
     * reading that would flap on every sample.
     */
    fun headroom(nowMillis: Long = System.currentTimeMillis()): Reading<Float> {
        val manager = powerManager
        if (manager == null) {
            lastHeadroomReading = Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE)
            return lastHeadroomReading
        }
        if (nowMillis - lastHeadroomReadAtMillis < HEADROOM_CACHE_MILLIS) return lastHeadroomReading

        val raw = runCatching { manager.getThermalHeadroom(FORECAST_SECONDS) }.getOrNull()
        lastHeadroomReadAtMillis = nowMillis
        lastHeadroomReading = PlatformValues.headroomReading(raw)
        return lastHeadroomReading
    }

    fun snapshot(nowMillis: Long = System.currentTimeMillis()): ThermalSnapshot = ThermalSnapshot(
        thermalStatus = _thermalStatus.value,
        headroom = headroom(nowMillis)
    )

    companion object {
        /**
         * One second is the platform's own stated floor for meaningful calls.
         * The sampling policy's minimum interval is five seconds, so in practice
         * one read happens per sample.
         */
        const val HEADROOM_CACHE_MILLIS = 1_000L

        /** Forecast horizon recommended by the platform performance guidance. */
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

package com.thermalsentinel.engine.collector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.thermalsentinel.engine.domain.AbsenceReason
import com.thermalsentinel.engine.domain.BatteryCapacityLevel
import com.thermalsentinel.engine.domain.BatteryHealth
import com.thermalsentinel.engine.domain.BatterySnapshot
import com.thermalsentinel.engine.domain.ChargingStatus
import com.thermalsentinel.engine.domain.PlatformValues
import com.thermalsentinel.engine.domain.PowerSource
import com.thermalsentinel.engine.domain.Reading

/**
 * The raw values a battery broadcast carried, kept as nullable ints so that
 * "this OEM did not send the extra" survives the trip to the domain layer.
 */
private data class RawBatteryBroadcast(
    val observedAtMillis: Long,
    val level: Int?,
    val scale: Int?,
    val temperatureDeciCelsius: Int?,
    val voltageMilliVolts: Int?,
    val status: Int?,
    val pluggedBits: Int?,
    val health: Int?,
    val capacityLevel: Int?,
    val cycleCount: Int?,
    val technology: String?,
    val present: Boolean?
) {
    companion object {
        val EMPTY = RawBatteryBroadcast(
            observedAtMillis = 0L,
            level = null,
            scale = null,
            temperatureDeciCelsius = null,
            voltageMilliVolts = null,
            status = null,
            pluggedBits = null,
            health = null,
            capacityLevel = null,
            cycleCount = null,
            technology = null,
            present = null
        )
    }
}

/**
 * Battery state from `ACTION_BATTERY_CHANGED` plus the `BatteryManager`
 * property API.
 *
 * Two deliberate choices:
 *
 *  - The broadcast is registered with `RECEIVER_NOT_EXPORTED`. It is a system
 *    broadcast, so delivery is unaffected, and the receiver stays unreachable
 *    from other apps.
 *  - The properties that the broadcast does not carry (instantaneous and average
 *    current, charge counter, energy counter) are read once per sample rather
 *    than by polling: a handful of binder reads every 15–60 seconds.
 */
class BatteryCollector(private val context: Context) {

    private val batteryManager: BatteryManager? =
        context.getSystemService(BatteryManager::class.java)

    @Volatile
    private var cached: RawBatteryBroadcast = RawBatteryBroadcast.EMPTY

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent != null) ingest(intent, System.currentTimeMillis())
        }
    }

    fun start() {
        if (registered) return
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
        // The broadcast is sticky, so a fresh registration immediately yields the
        // current state; there is never a "waiting for the first broadcast" hole.
        if (sticky != null) ingest(sticky, System.currentTimeMillis())
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    /** True when the last broadcast actually carried a battery temperature. */
    fun hasTemperatureReading(): Boolean = cached.temperatureDeciCelsius != null

    fun lastObservedAtMillis(): Long = cached.observedAtMillis

    /**
     * A complete snapshot for one sample: cached broadcast values plus a fresh
     * read of the property-based values.
     */
    fun snapshot(nowMillis: Long = System.currentTimeMillis()): BatterySnapshot {
        val raw = cached
        val manager = batteryManager

        val currentProperty = manager?.let { readIntProperty(it, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
        val averageProperty = manager?.let { readIntProperty(it, BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) }
        val chargeCounterProperty = manager?.let { readIntProperty(it, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) }
        val energyCounterProperty = manager?.let { readLongProperty(it, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) }
        val capacityProperty = manager?.let { readIntProperty(it, BatteryManager.BATTERY_PROPERTY_CAPACITY) }
        val statusProperty = manager?.let { readIntProperty(it, BatteryManager.BATTERY_PROPERTY_STATUS) }

        val percentFromExtra = PlatformValues.batteryPercentReading(raw.level, raw.scale)
        val percent = if (percentFromExtra.isPresent) percentFromExtra else percentFromProperty(capacityProperty)

        val chargingStatus = when {
            raw.status != null -> Reading.Present(ChargingStatus.fromPlatformStatus(raw.status))
            statusProperty != null && statusProperty != PlatformValues.INT_UNSUPPORTED_SENTINEL ->
                Reading.Present(ChargingStatus.fromPlatformStatus(statusProperty))
            else -> Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        }

        return BatterySnapshot(
            observedAtMillis = if (raw.observedAtMillis == 0L) nowMillis else raw.observedAtMillis,
            batteryPercent = percent,
            temperatureC = PlatformValues.temperatureReading(raw.temperatureDeciCelsius),
            voltageMilliVolts = PlatformValues.voltageReading(raw.voltageMilliVolts),
            currentMicroAmps = PlatformValues.currentReading(currentProperty),
            averageCurrentMicroAmps = PlatformValues.currentReading(averageProperty),
            chargeCounterMicroAmpHours = PlatformValues.counterReading(chargeCounterProperty),
            energyCounterNanoWattHours = PlatformValues.longPropertyReading(energyCounterProperty),
            chargingStatus = chargingStatus,
            plugged = raw.pluggedBits
                ?.let { Reading.Present(PowerSource.fromPluginBits(it)) }
                ?: Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM),
            health = raw.health
                ?.let { Reading.Present(BatteryHealth.fromPlatformHealth(it)) }
                ?: Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM),
            capacityLevel = raw.capacityLevel
                ?.let { Reading.Present(BatteryCapacityLevel.fromPlatformValue(it)) }
                ?: Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE),
            cycleCount = PlatformValues.cycleCountReading(raw.cycleCount),
            technology = raw.technology
                ?.let { Reading.Present(it) }
                ?: Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM),
            present = raw.present
                ?.let { Reading.Present(it) }
                ?: Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        )
    }

    private fun percentFromProperty(rawProperty: Int?): Reading<Int> {
        if (rawProperty == null) return Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        val reading = PlatformValues.intPropertyReading(rawProperty)
        if (!reading.isPresent) return reading
        val value = reading.valueOrNull ?: return Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        return if (value in 0..100) {
            Reading.Present(value)
        } else {
            Reading.Absent(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE)
        }
    }

    private fun ingest(intent: Intent, nowMillis: Long) {
        cached = RawBatteryBroadcast(
            observedAtMillis = nowMillis,
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, Int.MIN_VALUE)
                .takeIf { it > 0 },
            temperatureDeciCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            voltageMilliVolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            pluggedBits = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            capacityLevel = readCapacityLevelExtra(intent),
            cycleCount = readCycleCountExtra(intent),
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            present = if (intent.hasExtra(BatteryManager.EXTRA_PRESENT)) {
                intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
            } else {
                null
            }
        )
    }

    /** `EXTRA_CYCLE_COUNT` is API 34+. */
    private fun readCycleCountExtra(intent: Intent): Int? =
        if (Build.VERSION.SDK_INT >= 34) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }
        } else {
            null
        }

    /** `EXTRA_CAPACITY_LEVEL` is API 36+. */
    private fun readCapacityLevelExtra(intent: Intent): Int? =
        if (Build.VERSION.SDK_INT >= 36) {
            intent.getIntExtra(BatteryManager.EXTRA_CAPACITY_LEVEL, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }
        } else {
            null
        }

    /**
     * OEM builds occasionally throw from the property API instead of returning
     * the documented sentinel. A failure to answer is reported as "not
     * reported", never as a value.
     */
    private fun readIntProperty(manager: BatteryManager, property: Int): Int? =
        runCatching { manager.getIntProperty(property) }.getOrNull()

    private fun readLongProperty(manager: BatteryManager, property: Int): Long? =
        runCatching { manager.getLongProperty(property) }.getOrNull()
}

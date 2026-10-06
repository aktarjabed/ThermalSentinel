package com.thermalsentinel.engine.export

import com.thermalsentinel.engine.domain.DeviceSample
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One exportable row.
 *
 * Deliberately flat and explicit: an export is a data artefact, and a column that
 * silently disappears because a nullable field was renamed is worse than a wide
 * table. Every measurement is nullable, and `null` renders as an **empty field**,
 * which is the CSV spelling of "the platform did not report this".
 */
data class CsvRow(
    val timestampMillis: Long,
    val batteryTemperatureC: Float?,
    val androidThermalStatus: String?,
    val thermalHeadroom: Float?,
    val batteryPercent: Int?,
    val chargingStatus: String?,
    val powerSource: String?,
    val currentMicroAmps: Int?,
    val averageCurrentMicroAmps: Int?,
    val voltageMilliVolts: Int?,
    val chargeCounterMicroAmpHours: Int?,
    val batteryHealth: String?,
    val cycleCount: Int?,
    val sampleReason: String,
    val requestedIntervalMillis: Long,
    val actualIntervalMillis: Long?,
    val screenInteractive: Boolean
) {
    companion object {
        /** Drops the *reason* a value was absent: an export carries measurements, not absence metadata. */
        fun from(sample: DeviceSample, screenInteractive: Boolean = sample.sampling.screenInteractive): CsvRow =
            CsvRow(
                timestampMillis = sample.timestampMillis,
                batteryTemperatureC = sample.battery.temperatureC.valueOrNull,
                androidThermalStatus = sample.thermal.thermalStatus.valueOrNull?.storageValue,
                thermalHeadroom = sample.thermal.headroom.valueOrNull,
                batteryPercent = sample.battery.batteryPercent.valueOrNull,
                chargingStatus = sample.battery.chargingStatus.valueOrNull?.storageValue,
                powerSource = sample.battery.plugged.valueOrNull?.storageValue,
                currentMicroAmps = sample.battery.currentMicroAmps.valueOrNull,
                averageCurrentMicroAmps = sample.battery.averageCurrentMicroAmps.valueOrNull,
                voltageMilliVolts = sample.battery.voltageMilliVolts.valueOrNull,
                chargeCounterMicroAmpHours = sample.battery.chargeCounterMicroAmpHours.valueOrNull,
                batteryHealth = sample.battery.health.valueOrNull?.storageValue,
                cycleCount = sample.battery.cycleCount.valueOrNull,
                sampleReason = sample.sampling.reason.storageValue,
                requestedIntervalMillis = sample.sampling.requestedIntervalMillis,
                actualIntervalMillis = sample.sampling.actualIntervalMillis,
                screenInteractive = screenInteractive
            )
    }
}

/**
 * RFC 4180 CSV rendering.
 *
 * Two rules matter more than style here:
 *
 *  - **Empty means "not measured".** A missing temperature is an empty field, never
 *    `0`. A spreadsheet opening this file shows a blank cell, which is the truth.
 *  - **Numbers use `Locale.ROOT`.** A locale-formatted decimal comma would corrupt
 *    the file's structure on a device whose locale uses one.
 */
object ThermalCsv {

    val HEADER: String = listOf(
        "timestamp_iso",
        "timestamp_epoch_millis",
        "battery_temperature_c",
        "android_thermal_status",
        "thermal_headroom",
        "battery_percent",
        "charging_status",
        "power_source",
        "current_now_ua",
        "current_average_ua",
        "voltage_mv",
        "charge_counter_uah",
        "battery_health",
        "cycle_count",
        "sample_reason",
        "requested_interval_ms",
        "actual_interval_ms",
        "screen_interactive"
    ).joinToString(",")

    fun render(rows: List<CsvRow>): String = buildString {
        append(HEADER).append('\n')
        rows.forEach { row -> append(renderRow(row)).append('\n') }
    }

    fun renderRow(row: CsvRow): String = listOf(
        field(isoTimestamp(row.timestampMillis)),
        row.timestampMillis.toString(),
        decimal(row.batteryTemperatureC, decimals = 1),
        field(row.androidThermalStatus),
        decimal(row.thermalHeadroom, decimals = 3),
        field(row.batteryPercent?.toString()),
        field(row.chargingStatus),
        field(row.powerSource),
        field(row.currentMicroAmps?.toString()),
        field(row.averageCurrentMicroAmps?.toString()),
        field(row.voltageMilliVolts?.toString()),
        field(row.chargeCounterMicroAmpHours?.toString()),
        field(row.batteryHealth),
        field(row.cycleCount?.toString()),
        field(row.sampleReason),
        row.requestedIntervalMillis.toString(),
        field(row.actualIntervalMillis?.toString()),
        if (row.screenInteractive) "true" else "false"
    ).joinToString(",")

    /** `Locale.ROOT` so the decimal separator never depends on the device locale. */
    fun decimal(value: Float?, decimals: Int): String =
        if (value == null) "" else String.format(Locale.ROOT, "%.${decimals}f", value)

    /**
     * ISO-8601 with the device's UTC offset. The offset is included because a
     * reader in another time zone must not have to guess which local time this was.
     */
    fun isoTimestamp(timestampMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        TIMESTAMP_FORMAT.withZone(zoneId).format(Instant.ofEpochMilli(timestampMillis))

    /** Quotes a field only when CSV requires it, doubling embedded quotes. */
    fun field(value: String?): String {
        if (value == null || value.isEmpty()) return ""
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return '"' + value.replace("\"", "\"\"") + '"'
    }

    private val TIMESTAMP_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT)
}

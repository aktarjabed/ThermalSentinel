package com.thermalsentinel.engine.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room schema, version 1.
 *
 * Conventions used by every table below:
 *
 *  - Column names are the Kotlin property names (no `@ColumnInfo` renames), so a
 *    `@Query` string and the schema cannot drift apart by typo.
 *  - **Nullable columns mean "the platform did not give us a value".** Loading a
 *    history row back therefore restores an `Absent` reading rather than a zero.
 *    A 0 °C sample and an unmeasured sample stay distinguishable forever.
 *  - Enum values are stored through their `storageValue` strings, which come from
 *    the frozen contract enums. Unknown strings fail soft to the enum default.
 *  - `exportSchema = false` for now: schema export and migration tests are only
 *    meaningful once the schema has a second version, and a hand-written schema
 *    file that no build generated would be a lie. The policy is recorded in
 *    `docs/CORE_ENGINE_SPEC.md`: no destructive fallback, explicit migrations only.
 */

@Entity(
    tableName = "thermal_samples",
    indices = [Index(value = ["timestampMillis"]), Index(value = ["batteryObservedAtMillis"])]
)
data class ThermalSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMillis: Long,
    val batteryTemperatureDeciCelsius: Int?,
    val thermalStatus: String?,
    val headroomMilli: Int?,
    val batteryPercent: Int?,
    val chargingStatus: String?,
    val powerSource: String?,
    val currentMicroAmps: Int?,
    val averageCurrentMicroAmps: Int?,
    val voltageMilliVolts: Int?,
    val chargeCounterMicroAmpHours: Int?,
    val energyCounterNanoWattHours: Long?,
    val health: String?,
    val capacityLevel: String?,
    val cycleCount: Int?,
    /** [com.thermalsentinel.engine.domain.SampleReason] storage value. */
    val sampleReason: String,
    val requestedIntervalMillis: Long,
    val actualIntervalMillis: Long?,
    val screenInteractive: Boolean,
    /** When the battery broadcast behind this sample was actually received. */
    val batteryObservedAtMillis: Long
)

/**
 * Compacted history. The same table holds hourly and daily resolutions because
 * they share a shape; `bucketMillis` is part of the primary key so the two
 * resolutions can coexist without colliding.
 */
@Entity(
    tableName = "thermal_aggregates",
    primaryKeys = ["bucketStartMillis", "bucketMillis"],
    indices = [Index(value = ["bucketStartMillis"])]
)
data class ThermalAggregateEntity(
    val bucketStartMillis: Long,
    val bucketMillis: Long,
    val sampleCount: Int,
    val minTemperatureDeciCelsius: Int?,
    val maxTemperatureDeciCelsius: Int?,
    /** Sum of samples; average is derived as sum / sampleCount to avoid drift. */
    val temperatureSumDeciCelsius: Long,
    /** Highest `PowerManager` thermal level seen in the bucket, or null. */
    val maxThermalStatusLevel: Int?,
    val maxHeadroomMilli: Int?,
    val chargingSampleCount: Int,
    /**
     * Samples at or above 40 °C. A fixed physical reference, not the user's
     * threshold, because a stored count cannot be recomputed when the threshold
     * changes later.
     */
    val samplesAtOrAbove40C: Int
)

@Entity(
    tableName = "thermal_events",
    indices = [Index(value = ["timestampMillis"]), Index(value = ["type"])]
)
data class ThermalEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestampMillis: Long,
    /** [com.thermalsentinel.engine.domain.ThermalEventType] storage value. */
    val type: String,
    /** [com.thermalsentinel.engine.alert.AlertLevel] storage value, when relevant. */
    val level: String?,
    /** [com.thermalsentinel.engine.alert.AlertTrigger] storage value, when relevant. */
    val trigger: String?,
    val temperatureDeciCelsius: Int?,
    val thermalStatus: String?,
    val detail: String?
)

@Entity(
    tableName = "charging_sessions",
    indices = [Index(value = ["startMillis"])]
)
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startMillis: Long,
    /** Null while the session is still open. */
    val endMillis: Long?,
    val startPercent: Int?,
    val endPercent: Int?,
    val peakTemperatureDeciCelsius: Int?,
    val peakHeadroomMilli: Int?,
    /** Running sum, for an exact average without storing every sample twice. */
    val temperatureSumDeciCelsius: Long,
    val sampleCount: Int,
    val powerSource: String?
)

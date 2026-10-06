package com.thermalsentinel.engine.data

import com.thermalsentinel.engine.alert.AlertLevel
import com.thermalsentinel.engine.alert.AlertTrigger
import com.thermalsentinel.engine.domain.*
import com.thermalsentinel.engine.monitoring.HistoryMath
import com.thermalsentinel.engine.monitoring.RetentionPolicy
import com.thermalsentinel.engine.monitoring.SamplingPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What happened while storing one sample. Surfaced in Diagnostics. */
data class RecordOutcome(
    val sampleId: Long,
    val rateEvent: ThermalEventType?,
    val chargingSessionChanged: Boolean,
    val gapDetected: Boolean,
    val maintenance: MaintenanceResult?
)

data class MaintenanceResult(
    val compactedHourlyBuckets: Int = 0,
    val compactedDailyBuckets: Int = 0,
    val deletedSamples: Int = 0,
    val deletedHourlyBuckets: Int = 0,
    val deletedDailyBuckets: Int = 0,
    val deletedEvents: Int = 0,
    /**
     * Reported apart from [deletedEvents] on purpose. A charging session lives in
     * its own table; folding its deletions into the event count would make the
     * number in Diagnostics describe rows that were never events.
     */
    val deletedChargingSessions: Int = 0
)

data class RowCounts(
    val samples: Long,
    val hourlyBuckets: Long,
    val dailyBuckets: Long,
    val events: Long,
    val chargingSessions: Long,
    val oldestSampleMillis: Long?
)

/**
 * Statistics for a window.
 *
 * [timeAboveThresholdMillis] is null when the window is served from compacted
 * buckets: the time above *the user's current threshold* cannot be recomputed
 * from stored bucket counts, and inventing a number would be worse than saying
 * it is unavailable for that range.
 */
data class ThermalStatistics(
    val windowMillis: Long,
    val sampleCount: Int,
    val minTemperatureC: Float?,
    val maxTemperatureC: Float?,
    val averageTemperatureC: Float?,
    val peakThermalStatusLevel: Int?,
    val peakHeadroom: Float?,
    val timeAboveThresholdMillis: Long?,
    val thresholdC: Float,
    val chargingSampleCount: Int,
    val gapCount: Int,
    val derivedFromCompactedBuckets: Boolean
)

data class ThermalEventSummary(
    val timestampMillis: Long,
    val type: ThermalEventType,
    val level: AlertLevel?,
    val trigger: AlertTrigger?,
    val temperatureC: Float?,
    val thermalStatus: ThermalStatusBand?,
    val detail: String?
)

data class ChargingSessionSummary(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long?,
    val startPercent: Int?,
    val endPercent: Int?,
    val peakTemperatureC: Float?,
    val averageTemperatureC: Float?,
    val peakHeadroom: Float?,
    val powerSourceLabel: String,
    val sampleCount: Int
) {
    val isOpen: Boolean get() = endMillis == null

    val durationMillis: Long?
        get() = endMillis?.let { it - startMillis }

    /** Charge speed. Null unless the session ran long enough to mean anything. */
    val percentPerHour: Float?
        get() {
            val duration = durationMillis ?: return null
            val from = startPercent ?: return null
            val to = endPercent ?: return null
            if (duration < 10 * 60_000L) return null
            return (to - from) / (duration / 3_600_000f)
        }
}

/**
 * Everything the engine writes and reads about its own history.
 *
 * The repository owns three responsibilities that must not leak into the UI:
 * persisting samples, deriving timeline events from those samples, and running
 * the retention ladder.
 */
class ThermalHistoryRepository(
    private val database: EngineDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val sampleDao = database.sampleDao()
    private val aggregateDao = database.aggregateDao()
    private val eventDao = database.eventDao()
    private val sessionDao = database.chargingSessionDao()

    private val _latestSample = MutableStateFlow<DeviceSample?>(null)

    /** Latest sample, kept in memory so every screen sees the same value. */
    val latestSample: StateFlow<DeviceSample?> = _latestSample.asStateFlow()

    private val _lastOutcome = MutableStateFlow<RecordOutcome?>(null)
    val lastOutcome: StateFlow<RecordOutcome?> = _lastOutcome.asStateFlow()

    private var lastMaintenanceMillis = 0L

    suspend fun loadLatest(): DeviceSample? {
        val entity = sampleDao.latest() ?: return null
        val domain = SampleMappers.toDomain(entity)
        _latestSample.value = domain
        return domain
    }

    /**
     * Stores one sample and everything derived from it. Intended to be called
     * only by the monitoring coordinator.
     */
    suspend fun record(sample: DeviceSample): RecordOutcome {
        val id = sampleDao.insert(SampleMappers.toEntity(sample))

        val chargingChanged = updateChargingSession(sample)
        val rateEvent = detectRateEvent(sample)
        val gapDetected = sample.sampling.isGap
        if (gapDetected) {
            eventDao.insert(
                ThermalEventEntity(
                    timestampMillis = sample.timestampMillis,
                    type = ThermalEventType.GAP_DETECTED.storageValue,
                    level = null,
                    trigger = null,
                    temperatureDeciCelsius = sample.batteryTemperatureC.valueOrNull
                        ?.let(PlatformValues::celsiusToDeciCelsius),
                    thermalStatus = sample.thermal.thermalStatus.valueOrNull?.storageValue,
                    detail = "Sampling interval was ${sample.sampling.actualIntervalMillis} ms " +
                        "against a requested ${sample.sampling.requestedIntervalMillis} ms."
                )
            )
        }

        val maintenance = runMaintenanceIfDue(sample.timestampMillis)

        _latestSample.value = sample
        val outcome = RecordOutcome(
            sampleId = id,
            rateEvent = rateEvent,
            chargingSessionChanged = chargingChanged,
            gapDetected = gapDetected,
            maintenance = maintenance
        )
        _lastOutcome.value = outcome
        return outcome
    }

    suspend fun recordEvent(
        timestampMillis: Long,
        type: ThermalEventType,
        level: AlertLevel? = null,
        trigger: AlertTrigger? = null,
        temperatureC: Float? = null,
        thermalStatus: ThermalStatusBand? = null,
        detail: String? = null
    ) {
        eventDao.insert(
            ThermalEventEntity(
                timestampMillis = timestampMillis,
                type = type.storageValue,
                level = level?.storageValue,
                trigger = trigger?.storageValue,
                temperatureDeciCelsius = temperatureC?.let(PlatformValues::celsiusToDeciCelsius),
                thermalStatus = thermalStatus?.storageValue,
                detail = detail
            )
        )
    }

    /**
     * Raw temperature series for a window served from un-compacted samples.
     * Points with no measurement stay null so the chart draws a hole.
     */
    suspend fun rawSeries(fromMillis: Long): List<HistoryMath.SeriesPoint> =
        HistoryMath.seriesPoints(sampleDao.since(fromMillis).map(SampleMappers::toSeriesPoint))

    /**
     * Full samples for a window, used by the CSV export.
     *
     * Only the sample table is served here: hourly and daily rows are averages,
     * and exporting an average as though it were a measurement would make the
     * file misleading. Absent values come back as `Absent(NOT_RECORDED)` so the
     * exporter writes an empty field rather than a zero.
     */
    suspend fun samplesSince(fromMillis: Long): List<DeviceSample> =
        sampleDao.since(fromMillis).map(SampleMappers::toDomain)

    /**
     * Series served from compacted buckets (hourly or daily) for ranges beyond
     * raw retention. Values are bucket averages, which is exactly why the chart
     * labels them as averages.
     */
    suspend fun compactedSeries(bucketMillis: Long, fromMillis: Long): List<HistoryMath.SeriesPoint> =
        HistoryMath.seriesPoints(
            aggregateDao.since(bucketMillis, fromMillis).map { row ->
                val average = SampleMappers.aggregateAverageDeciCelsius(row)
                row.bucketStartMillis to average?.let { PlatformValues.deciCelsiusToCelsius(it.toInt()) }
            }
        )

    /** Series for the 24 h range: raw samples, with real gaps preserved. */
    suspend fun series24h(nowMillis: Long = clock()): List<HistoryMath.SeriesPoint> =
        rawSeries(nowMillis - RetentionPolicy.DAY_MILLIS)

    suspend fun statistics(
        windowMillis: Long,
        warningThresholdC: Float,
        nowMillis: Long = clock()
    ): ThermalStatistics {
        val from = nowMillis - windowMillis
        val maxGap = SamplingPolicy.SCREEN_OFF_INTERVAL_MS * 3

        if (windowMillis <= RetentionPolicy.RAW_RETENTION_DAYS * RetentionPolicy.DAY_MILLIS) {
            val rows = sampleDao.since(from)
            val points = HistoryMath.seriesPoints(rows.map(SampleMappers::toSeriesPoint))
            val gapCount = rows.zipWithNext().count { (a, b) ->
                b.timestampMillis - a.timestampMillis > a.requestedIntervalMillis * SamplingMetadata.GAP_FACTOR
            }
            return ThermalStatistics(
                windowMillis = windowMillis,
                sampleCount = rows.size,
                minTemperatureC = HistoryMath.minTemperatureC(points),
                maxTemperatureC = HistoryMath.maxTemperatureC(points),
                averageTemperatureC = HistoryMath.averageTemperatureC(points),
                peakThermalStatusLevel = rows.mapNotNull { it.thermalStatus }
                    .maxOfOrNull { ThermalStatusBand.fromStorage(it).androidLevel },
                peakHeadroom = rows.mapNotNull { it.headroomMilli }.maxOrNull()
                    ?.let(PlatformValues::milliToHeadroom),
                timeAboveThresholdMillis = HistoryMath.timeAboveThresholdMillis(points, warningThresholdC, maxGap),
                thresholdC = warningThresholdC,
                chargingSampleCount = rows.count { row ->
                    ChargingStatus.fromStorage(row.chargingStatus).activelyCharging ||
                        PowerSource.fromStorage(row.powerSource).isExternalPower
                },
                gapCount = gapCount,
                derivedFromCompactedBuckets = false
            )
        }

        val bucketMillis = if (windowMillis <= RetentionPolicy.HOURLY_RETENTION_DAYS * RetentionPolicy.DAY_MILLIS) {
            RetentionPolicy.HOURLY_BUCKET_MILLIS
        } else {
            RetentionPolicy.DAILY_BUCKET_MILLIS
        }
        val buckets = aggregateDao.since(bucketMillis, from)
        val totalSamples = buckets.sumOf { it.sampleCount }
        val totalSum = buckets.sumOf { it.temperatureSumDeciCelsius }
        val minDeci = buckets.mapNotNull { it.minTemperatureDeciCelsius }.minOrNull()
        val maxDeci = buckets.mapNotNull { it.maxTemperatureDeciCelsius }.maxOrNull()
        return ThermalStatistics(
            windowMillis = windowMillis,
            sampleCount = totalSamples,
            minTemperatureC = minDeci?.let { PlatformValues.deciCelsiusToCelsius(it) },
            maxTemperatureC = maxDeci?.let { PlatformValues.deciCelsiusToCelsius(it) },
            averageTemperatureC = if (totalSamples > 0) {
                PlatformValues.deciCelsiusToCelsius((totalSum / totalSamples).toInt())
            } else {
                null
            },
            peakThermalStatusLevel = buckets.mapNotNull { it.maxThermalStatusLevel }.maxOrNull(),
            peakHeadroom = buckets.mapNotNull { it.maxHeadroomMilli }.maxOrNull()
                ?.let(PlatformValues::milliToHeadroom),
            timeAboveThresholdMillis = null,
            thresholdC = warningThresholdC,
            chargingSampleCount = buckets.sumOf { it.chargingSampleCount },
            gapCount = 0,
            derivedFromCompactedBuckets = true
        )
    }

    suspend fun recentEvents(limit: Int = 40): List<ThermalEventSummary> =
        eventDao.recent(limit).map(::toEventSummary)

    suspend fun eventsSince(fromMillis: Long): List<ThermalEventSummary> =
        eventDao.since(fromMillis).map(::toEventSummary)

    suspend fun chargingSessions(limit: Int = 10): List<ChargingSessionSummary> =
        sessionDao.recent(limit).map(::toSessionSummary)

    suspend fun openChargingSession(): ChargingSessionSummary? =
        sessionDao.openSession()?.let(::toSessionSummary)

    suspend fun rowCounts(): RowCounts = RowCounts(
        samples = sampleDao.count(),
        hourlyBuckets = aggregateDao.count(RetentionPolicy.HOURLY_BUCKET_MILLIS),
        dailyBuckets = aggregateDao.count(RetentionPolicy.DAILY_BUCKET_MILLIS),
        events = eventDao.count(),
        chargingSessions = sessionDao.count(),
        oldestSampleMillis = sampleDao.oldestTimestampMillis()
    )

    suspend fun eventsSinceCount(fromMillis: Long): Long = eventDao.countSince(fromMillis)

    suspend fun runMaintenanceIfDue(nowMillis: Long = clock()): MaintenanceResult? {
        if (nowMillis - lastMaintenanceMillis < MAINTENANCE_INTERVAL_MILLIS) return null
        return runMaintenance(nowMillis)
    }

    /**
     * Retention ladder. Buckets are compacted and deleted only on whole-bucket
     * boundaries, so a bucket can never be compacted twice with partial data —
     * the first pass's contribution would otherwise be overwritten by the
     * second. The cost of that guarantee is that raw data can outlive its
     * retention window by less than one bucket.
     */
    suspend fun runMaintenance(nowMillis: Long = clock()): MaintenanceResult {
        lastMaintenanceMillis = nowMillis
        var result = MaintenanceResult()

        // Raw samples -> hourly buckets.
        val rawBoundary = RetentionPolicy.bucketStart(
            RetentionPolicy.rawCutoffMillis(nowMillis),
            RetentionPolicy.HOURLY_BUCKET_MILLIS
        )
        val toCompact = sampleDao.before(rawBoundary)
        if (toCompact.isNotEmpty()) {
            val buckets = toCompact
                .groupBy { RetentionPolicy.bucketStart(it.timestampMillis, RetentionPolicy.HOURLY_BUCKET_MILLIS) }
                .map { (bucketStart, rows) -> aggregateSamples(bucketStart, rows) }
            aggregateDao.upsertAll(buckets)
            result = result.copy(compactedHourlyBuckets = buckets.size)
        }
        val deletedSamples = sampleDao.deleteBefore(rawBoundary)

        // Hourly buckets -> daily buckets.
        val hourlyBoundary = RetentionPolicy.bucketStart(
            RetentionPolicy.hourlyCutoffMillis(nowMillis),
            RetentionPolicy.DAILY_BUCKET_MILLIS
        )
        val oldHourly = aggregateDao.before(RetentionPolicy.HOURLY_BUCKET_MILLIS, hourlyBoundary)
        if (oldHourly.isNotEmpty()) {
            val daily = oldHourly
                .groupBy { RetentionPolicy.bucketStart(it.bucketStartMillis, RetentionPolicy.DAILY_BUCKET_MILLIS) }
                .map { (bucketStart, rows) -> mergeBuckets(bucketStart, rows) }
            aggregateDao.upsertAll(daily)
            result = result.copy(compactedDailyBuckets = daily.size)
        }
        val deletedHourly = aggregateDao.deleteBefore(RetentionPolicy.HOURLY_BUCKET_MILLIS, hourlyBoundary)
        val dailyBoundary = RetentionPolicy.dailyCutoffMillis(nowMillis)
        val deletedDaily = aggregateDao.deleteBefore(RetentionPolicy.DAILY_BUCKET_MILLIS, dailyBoundary)
        val deletedEvents = eventDao.deleteBefore(dailyBoundary)
        val deletedSessions = sessionDao.deleteClosedBefore(dailyBoundary)

        return result.copy(
            deletedSamples = deletedSamples,
            deletedHourlyBuckets = deletedHourly,
            deletedDailyBuckets = deletedDaily,
            deletedEvents = deletedEvents,
            deletedChargingSessions = deletedSessions
        )
    }

    // ------------------------------------------------------------------ internals

    private suspend fun updateChargingSession(sample: DeviceSample): Boolean {
        val open = sessionDao.openSession()
        val onPower = sample.battery.onExternalPower
        val deci = sample.battery.temperatureC.valueOrNull?.let(PlatformValues::celsiusToDeciCelsius)
        val headroomMilli = sample.thermal.headroom.valueOrNull?.let(PlatformValues::headroomToMilli)

        if (onPower && open == null) {
            val id = sessionDao.insert(
                ChargingSessionEntity(
                    startMillis = sample.timestampMillis,
                    endMillis = null,
                    startPercent = sample.battery.batteryPercent.valueOrNull,
                    endPercent = sample.battery.batteryPercent.valueOrNull,
                    peakTemperatureDeciCelsius = deci,
                    peakHeadroomMilli = headroomMilli,
                    temperatureSumDeciCelsius = (deci ?: 0).toLong(),
                    sampleCount = if (deci == null) 0 else 1,
                    powerSource = sample.battery.plugged.valueOrNull?.storageValue
                )
            )
            recordEvent(
                timestampMillis = sample.timestampMillis,
                type = ThermalEventType.CHARGING_STARTED,
                temperatureC = sample.battery.temperatureC.valueOrNull,
                thermalStatus = sample.thermal.thermalStatus.valueOrNull,
                detail = "Charging session $id started at ${sample.battery.batteryPercent.valueOrNull ?: "?"}%."
            )
            return true
        }

        if (!onPower && open != null) {
            sessionDao.update(
                open.copy(
                    endMillis = sample.timestampMillis,
                    endPercent = sample.battery.batteryPercent.valueOrNull
                )
            )
            recordEvent(
                timestampMillis = sample.timestampMillis,
                type = ThermalEventType.CHARGING_STOPPED,
                temperatureC = sample.battery.temperatureC.valueOrNull,
                thermalStatus = sample.thermal.thermalStatus.valueOrNull,
                detail = "Charging stopped at ${sample.battery.batteryPercent.valueOrNull ?: "?"}%."
            )
            return true
        }

        if (onPower && open != null) {
            sessionDao.update(
                open.copy(
                    endPercent = sample.battery.batteryPercent.valueOrNull,
                    peakTemperatureDeciCelsius = maxOfNullable(open.peakTemperatureDeciCelsius, deci),
                    peakHeadroomMilli = maxOfNullable(open.peakHeadroomMilli, headroomMilli),
                    temperatureSumDeciCelsius = open.temperatureSumDeciCelsius + (deci ?: 0).toLong(),
                    sampleCount = open.sampleCount + if (deci == null) 0 else 1
                )
            )
        }
        return false
    }

    /**
     * Heating and cooling detection for the timeline.
     *
     * The window is selected by **time**, never by row count. The sampling cadence
     * changes with the alert state, so "the last 24 rows" means ten minutes at the
     * normal cadence and barely two minutes at the five-second event cadence: a
     * count-based window would go blind exactly when the device is heating fastest,
     * because the samples it needs no longer fit in the count. The sample being
     * recorded is already committed by [record] when this runs, so it arrives in
     * this query and must not be appended a second time — a duplicate would weigh
     * the newest reading twice in the fit and add a point the engine never measured.
     */
    private suspend fun detectRateEvent(sample: DeviceSample): ThermalEventType? {
        val points = HistoryMath.seriesPoints(
            sampleDao.since(sample.timestampMillis - RATE_WINDOW_MILLIS)
                .map(SampleMappers::toSeriesPoint)
        )
        val verdict = HistoryMath.classifyRate(points, RATE_WINDOW_MILLIS)
        val candidate = when (verdict) {
            HistoryMath.RateVerdict.HEATING -> ThermalEventType.HEATING_EVENT
            HistoryMath.RateVerdict.COOLING -> ThermalEventType.COOLING_EVENT
            else -> return null
        }

        // Dedupe: the same verdict must not be written every sampling interval.
        val alreadyRecorded = eventDao.since(sample.timestampMillis - RATE_EVENT_DEDUPE_MILLIS)
            .any { it.type == candidate.storageValue }
        if (alreadyRecorded) return null

        val rate = HistoryMath.riseRateCPerMinute(points, RATE_WINDOW_MILLIS)
        recordEvent(
            timestampMillis = sample.timestampMillis,
            type = candidate,
            temperatureC = sample.battery.temperatureC.valueOrNull,
            thermalStatus = sample.thermal.thermalStatus.valueOrNull,
            detail = rate?.let { String.format(java.util.Locale.ROOT, "%+.2f °C/min over %d min", it, RATE_WINDOW_MILLIS / 60_000L) }
        )
        return candidate
    }

    private fun aggregateSamples(bucketStartMillis: Long, rows: List<ThermalSampleEntity>): ThermalAggregateEntity {
        val temperatures = rows.mapNotNull { it.batteryTemperatureDeciCelsius }
        return ThermalAggregateEntity(
            bucketStartMillis = bucketStartMillis,
            bucketMillis = RetentionPolicy.HOURLY_BUCKET_MILLIS,
            sampleCount = rows.size,
            minTemperatureDeciCelsius = temperatures.minOrNull(),
            maxTemperatureDeciCelsius = temperatures.maxOrNull(),
            temperatureSumDeciCelsius = temperatures.sumOf { it.toLong() },
            maxThermalStatusLevel = rows.mapNotNull { it.thermalStatus }
                .maxOfOrNull { ThermalStatusBand.fromStorage(it).androidLevel },
            maxHeadroomMilli = rows.mapNotNull { it.headroomMilli }.maxOrNull(),
            chargingSampleCount = rows.count { row ->
                ChargingStatus.fromStorage(row.chargingStatus).activelyCharging ||
                    PowerSource.fromStorage(row.powerSource).isExternalPower
            },
            samplesAtOrAbove40C = temperatures.count { it >= WARM_REFERENCE_DECI_CELSIUS }
        )
    }

    private fun mergeBuckets(bucketStartMillis: Long, rows: List<ThermalAggregateEntity>): ThermalAggregateEntity =
        ThermalAggregateEntity(
            bucketStartMillis = bucketStartMillis,
            bucketMillis = RetentionPolicy.DAILY_BUCKET_MILLIS,
            sampleCount = rows.sumOf { it.sampleCount },
            minTemperatureDeciCelsius = rows.mapNotNull { it.minTemperatureDeciCelsius }.minOrNull(),
            maxTemperatureDeciCelsius = rows.mapNotNull { it.maxTemperatureDeciCelsius }.maxOrNull(),
            temperatureSumDeciCelsius = rows.sumOf { it.temperatureSumDeciCelsius },
            maxThermalStatusLevel = rows.mapNotNull { it.maxThermalStatusLevel }.maxOrNull(),
            maxHeadroomMilli = rows.mapNotNull { it.maxHeadroomMilli }.maxOrNull(),
            chargingSampleCount = rows.sumOf { it.chargingSampleCount },
            samplesAtOrAbove40C = rows.sumOf { it.samplesAtOrAbove40C }
        )

    private fun toEventSummary(entity: ThermalEventEntity): ThermalEventSummary = ThermalEventSummary(
        timestampMillis = entity.timestampMillis,
        type = ThermalEventType.fromStorage(entity.type) ?: ThermalEventType.GAP_DETECTED,
        level = AlertLevel.fromStorage(entity.level),
        trigger = AlertTrigger.fromStorage(entity.trigger),
        temperatureC = entity.temperatureDeciCelsius?.let(PlatformValues::deciCelsiusToCelsius),
        thermalStatus = entity.thermalStatus?.let(ThermalStatusBand::fromStorage),
        detail = entity.detail
    )

    private fun toSessionSummary(entity: ChargingSessionEntity): ChargingSessionSummary =
        ChargingSessionSummary(
            id = entity.id,
            startMillis = entity.startMillis,
            endMillis = entity.endMillis,
            startPercent = entity.startPercent,
            endPercent = entity.endPercent,
            peakTemperatureC = entity.peakTemperatureDeciCelsius
                ?.let(PlatformValues::deciCelsiusToCelsius),
            averageTemperatureC = if (entity.sampleCount > 0) {
                PlatformValues.deciCelsiusToCelsius(
                    (entity.temperatureSumDeciCelsius / entity.sampleCount).toInt()
                )
            } else {
                null
            },
            peakHeadroom = entity.peakHeadroomMilli?.let(PlatformValues::milliToHeadroom),
            powerSourceLabel = PowerSource.fromStorage(entity.powerSource).label,
            sampleCount = entity.sampleCount
        )

    private fun maxOfNullable(first: Int?, second: Int?): Int? = when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }

    companion object {
        const val MAINTENANCE_INTERVAL_MILLIS = 60 * 60 * 1000L

        /** Look-back for the rate detector, and the window `since()` is queried with. */
        const val RATE_WINDOW_MILLIS = 10 * 60 * 1000L
        const val RATE_EVENT_DEDUPE_MILLIS = 15 * 60 * 1000L

        /** 40.0 °C in tenths of a degree: the fixed reference used by aggregates. */
        const val WARM_REFERENCE_DECI_CELSIUS = 400
    }
}

package com.thermalsentinel.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thermalsentinel.ThermalSentinelApp
import com.thermalsentinel.engine.alert.RuleViolation
import com.thermalsentinel.engine.alert.ThermalAlertRules
import com.thermalsentinel.engine.data.ChargingSessionSummary
import com.thermalsentinel.engine.data.ThermalEventSummary
import com.thermalsentinel.engine.data.ThermalStatistics
import com.thermalsentinel.engine.diagnostics.ReadinessCheck
import com.thermalsentinel.engine.domain.DeviceSample
import com.thermalsentinel.engine.domain.ThermalStatusBand
import com.thermalsentinel.engine.monitoring.HistoryMath
import com.thermalsentinel.engine.monitoring.RetentionPolicy
import com.thermalsentinel.engine.monitoring.SamplingPolicy
import com.thermalsentinel.engine.export.ExportOutcome
import com.thermalsentinel.engine.export.ExportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Range chips on the history screen. */
enum class HistoryRange(val label: String, val windowMillis: Long) {
    DAY("24h", RetentionPolicy.DAY_MILLIS),
    WEEK("7d", 7 * RetentionPolicy.DAY_MILLIS),
    MONTH("30d", 30 * RetentionPolicy.DAY_MILLIS);

    /** Raw samples only exist for the 24 h range; longer ranges read buckets. */
    val usesRawSamples: Boolean
        get() = windowMillis <= RetentionPolicy.RAW_RETENTION_DAYS * RetentionPolicy.DAY_MILLIS

    val bucketMillis: Long
        get() = if (windowMillis <= RetentionPolicy.HOURLY_RETENTION_DAYS * RetentionPolicy.DAY_MILLIS) {
            RetentionPolicy.HOURLY_BUCKET_MILLIS
        } else {
            RetentionPolicy.DAILY_BUCKET_MILLIS
        }
}

/**
 * Shared plumbing for the screens that read history.
 *
 * Every subclass refreshes its queries whenever a new sample lands. The queries
 * are indexed and small (the 24 h window is at most a few thousand rows), and
 * refreshing on the sample stream is what keeps the screen and the ongoing
 * notification from disagreeing about the current temperature.
 */
abstract class HistoryReadingViewModel(application: Application) : AndroidViewModel(application) {

    protected val graph = (application as ThermalSentinelApp).graph

    val latestSample: StateFlow<DeviceSample?> = graph.history.latestSample

    protected fun observeSamples(refresh: suspend () -> Unit) {
        viewModelScope.launch {
            graph.history.latestSample.collect { sample ->
                if (sample != null) refresh()
            }
        }
    }
}

/** Thermal Monitor screen. */
class ThermalViewModel(application: Application) : HistoryReadingViewModel(application) {

    private val _series = MutableStateFlow<List<HistoryMath.SeriesPoint>>(emptyList())
    val series: StateFlow<List<HistoryMath.SeriesPoint>> = _series.asStateFlow()

    private val _statistics = MutableStateFlow<ThermalStatistics?>(null)
    val statistics: StateFlow<ThermalStatistics?> = _statistics.asStateFlow()

    private val _events = MutableStateFlow<List<ThermalEventSummary>>(emptyList())
    val events: StateFlow<List<ThermalEventSummary>> = _events.asStateFlow()

    val rules: StateFlow<ThermalAlertRules> = graph.settings.rules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThermalAlertRules.DEFAULT
    )

    init {
        observeSamples { refresh() }
        viewModelScope.launch { refresh() }
    }

    suspend fun refresh() {
        val now = System.currentTimeMillis()
        _series.value = graph.history.series24h(now)
        _statistics.value = graph.history.statistics(
            windowMillis = HistoryRange.DAY.windowMillis,
            warningThresholdC = rules.value.effectiveWarningThresholdC(
                latestSample.value?.battery?.onExternalPower == true
            ),
            nowMillis = now
        )
        _events.value = graph.history.recentEvents(limit = 12)
    }

    fun largestGapMillis(): Long = HistoryMath.largestGapMillis(series.value)

    fun maxGapForChart(): Long = SamplingPolicy.SCREEN_OFF_INTERVAL_MS * 3
}

/** Battery & Charging screen. */
class BatteryViewModel(application: Application) : HistoryReadingViewModel(application) {

    private val _sessions = MutableStateFlow<List<ChargingSessionSummary>>(emptyList())
    val sessions: StateFlow<List<ChargingSessionSummary>> = _sessions.asStateFlow()

    private val _openSession = MutableStateFlow<ChargingSessionSummary?>(null)
    val openSession: StateFlow<ChargingSessionSummary?> = _openSession.asStateFlow()

    private val _statistics = MutableStateFlow<ThermalStatistics?>(null)
    val statistics: StateFlow<ThermalStatistics?> = _statistics.asStateFlow()

    init {
        observeSamples { refresh() }
        viewModelScope.launch { refresh() }
    }

    suspend fun refresh() {
        _sessions.value = graph.history.chargingSessions(limit = 5)
        _openSession.value = graph.history.openChargingSession()
        _statistics.value = graph.history.statistics(
            windowMillis = HistoryRange.DAY.windowMillis,
            warningThresholdC = ThermalAlertRules.DEFAULT.warningThresholdC
        )
    }
}

/** Thermal History screen. */
class HistoryViewModel(application: Application) : HistoryReadingViewModel(application) {

    private val _range = MutableStateFlow(HistoryRange.DAY)
    val range: StateFlow<HistoryRange> = _range.asStateFlow()

    private val _series = MutableStateFlow<List<HistoryMath.SeriesPoint>>(emptyList())
    val series: StateFlow<List<HistoryMath.SeriesPoint>> = _series.asStateFlow()

    private val _statistics = MutableStateFlow<ThermalStatistics?>(null)
    val statistics: StateFlow<ThermalStatistics?> = _statistics.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChargingSessionSummary>>(emptyList())
    val sessions: StateFlow<List<ChargingSessionSummary>> = _sessions.asStateFlow()

    private val _exportState = MutableStateFlow<ExportOutcome?>(null)
    val exportState: StateFlow<ExportOutcome?> = _exportState.asStateFlow()

    val rules: StateFlow<ThermalAlertRules> = graph.settings.rules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThermalAlertRules.DEFAULT
    )

    init {
        observeSamples { refresh() }
        viewModelScope.launch { refresh() }
    }

    fun selectRange(value: HistoryRange) {
        _range.value = value
        viewModelScope.launch { refresh() }
    }

    suspend fun refresh() {
        val now = System.currentTimeMillis()
        val selected = _range.value
        val from = now - selected.windowMillis
        _series.value = if (selected.usesRawSamples) {
            graph.history.rawSeries(from)
        } else {
            graph.history.compactedSeries(selected.bucketMillis, from)
        }
        _statistics.value = graph.history.statistics(
            windowMillis = selected.windowMillis,
            warningThresholdC = rules.value.warningThresholdC,
            nowMillis = now
        )
        _sessions.value = graph.history.chargingSessions(limit = 6)
    }

    fun exportCsv() {
        viewModelScope.launch {
            _exportState.value = graph.exporter.exportSamplesCsv(_range.value.windowMillis)
        }
    }

    /**
     * Gap threshold for the chart. Three screen-off intervals is the same limit
     * the statistics use, so the chart and the numbers cannot disagree about what
     * counts as missing data.
     */
    fun maxGapForRange(): Long = SamplingPolicy.SCREEN_OFF_INTERVAL_MS * 3

    /** Share intent for a finished export; the screen starts the chooser. */
    fun shareIntent(result: ExportResult) = graph.exporter.shareIntent(result)

    fun clearExportState() {
        _exportState.value = null
    }
}

/** Alert Rules screen. */
class AlertRulesViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = (application as ThermalSentinelApp).graph

    val rules: StateFlow<ThermalAlertRules> = graph.settings.rules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThermalAlertRules.DEFAULT
    )

    private val _violations = MutableStateFlow<List<RuleViolation>>(emptyList())
    val violations: StateFlow<List<RuleViolation>> = _violations.asStateFlow()

    init {
        viewModelScope.launch {
            graph.settings.rules.collect { current -> _violations.value = current.validate() }
        }
    }

    fun setWarningEnabled(value: Boolean) = update { it.copy(warningEnabled = value) }

    fun setWarningThreshold(value: Float) = update { it.copy(warningThresholdC = value) }

    fun setCriticalEnabled(value: Boolean) = update { it.copy(criticalEnabled = value) }

    fun setCriticalThreshold(value: Float) = update { it.copy(criticalThresholdC = value) }

    fun setHysteresis(value: Float) = update { it.copy(hysteresisC = value) }

    fun setCooldownMinutes(value: Int) = update { it.copy(cooldownMinutes = value) }

    fun setChargingWarningEnabled(value: Boolean) = update { it.copy(chargingWarningEnabled = value) }

    fun setChargingThreshold(value: Float) = update { it.copy(chargingWarningThresholdC = value) }

    fun toggleStatusBand(band: ThermalStatusBand) = update { current ->
        val bands = current.statusBandTriggers.toMutableSet()
        if (!bands.add(band)) bands.remove(band)
        current.copy(statusBandTriggers = bands)
    }

    fun resetToDefaults() {
        viewModelScope.launch { graph.settings.resetRulesToDefaults() }
    }

    private fun update(transform: (ThermalAlertRules) -> ThermalAlertRules) {
        viewModelScope.launch { graph.settings.updateRules(transform) }
    }
}

/** Diagnostics screen. */
class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = (application as ThermalSentinelApp).graph

    private val _checks = MutableStateFlow<List<ReadinessCheck>>(emptyList())
    val checks: StateFlow<List<ReadinessCheck>> = _checks.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    val monitoringEnabled: StateFlow<Boolean> = graph.settings.monitoringEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    init {
        runChecks()
    }

    fun runChecks() {
        viewModelScope.launch {
            _running.value = true
            _checks.value = graph.diagnostics.runChecks()
            _running.value = false
        }
    }
}

package com.thermalsentinel.engine.service

import com.thermalsentinel.engine.alert.AlertAction
import com.thermalsentinel.engine.alert.AlertEngine
import com.thermalsentinel.engine.alert.AlertInput
import com.thermalsentinel.engine.alert.AlertLevel
import com.thermalsentinel.engine.alert.AlertNotificationPublisher
import com.thermalsentinel.engine.alert.AlertState
import com.thermalsentinel.engine.collector.BatteryCollector
import com.thermalsentinel.engine.collector.ThermalCollector
import com.thermalsentinel.engine.data.EngineSettingsRepository
import com.thermalsentinel.engine.data.ThermalHistoryRepository
import com.thermalsentinel.engine.domain.DeviceSample
import com.thermalsentinel.engine.domain.SampleReason
import com.thermalsentinel.engine.domain.ThermalEventType
import com.thermalsentinel.engine.monitoring.SampleAssembler
import com.thermalsentinel.engine.monitoring.SampleRequest
import com.thermalsentinel.engine.monitoring.SamplingContext
import com.thermalsentinel.engine.monitoring.SamplingDecision
import com.thermalsentinel.engine.monitoring.SamplingPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The sampling loop and the alert pipeline.
 *
 * Responsibilities, in order:
 *
 *  1. Ask [SamplingPolicy] how long to wait next, then wait exactly that long.
 *  2. Assemble a sample from the two collectors (battery broadcast + property
 *     reads, platform thermal status + headroom).
 *  3. Store it in local history.
 *  4. Evaluate the alert state machine and turn its actions into timeline events
 *     and notifications.
 *  5. Refresh the ongoing notification and hand the sample to the widget updater.
 *
 * Everything the loop needs from Android is injected, so the loop itself has no
 * platform calls of its own beyond reading the clock.
 */
class MonitoringCoordinator(
    private val settings: EngineSettingsRepository,
    private val history: ThermalHistoryRepository,
    private val batteryCollector: BatteryCollector,
    private val thermalCollector: ThermalCollector,
    private val publisher: AlertNotificationPublisher,
    private val interactiveProvider: () -> Boolean,
    private val onSampleStored: suspend (DeviceSample) -> Unit,
    private val scope: CoroutineScope
) {
    private val _alertState = MutableStateFlow(AlertState())
    val alertState: StateFlow<AlertState> = _alertState.asStateFlow()

    private val _samplingDecision = MutableStateFlow<SamplingDecision?>(null)
    val samplingDecision: StateFlow<SamplingDecision?> = _samplingDecision.asStateFlow()

    private val _alertExplanation = MutableStateFlow("No sample evaluated yet.")
    val alertExplanation: StateFlow<String> = _alertExplanation.asStateFlow()

    private var loopJob: Job? = null
    private var previousTimestampMillis: Long? = null
    private var lastStoredSample: DeviceSample? = null

    suspend fun restorePersistedState() {
        _alertState.value = settings.alertStateNow()
    }

    fun start() {
        batteryCollector.start()
        thermalCollector.start()
        if (loopJob != null) return
        loopJob = scope.launch { runLoop() }
    }

    /** Stops sampling and clears the ongoing notification. Safe to call twice. */
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        batteryCollector.stop()
        thermalCollector.stop()
        publisher.cancelMonitoringNotification()
    }

    /**
     * Takes one sample immediately, bypassing the schedule. Used by the service
     * on start and by an explicit user refresh.
     */
    suspend fun sampleNow(
        reason: SampleReason,
        requestedIntervalMillis: Long = SamplingPolicy.NORMAL_INTERVAL_MS
    ): DeviceSample {
        val nowMillis = System.currentTimeMillis()
        val battery = batteryCollector.snapshot(nowMillis)
        val thermal = thermalCollector.snapshot()
        val sample = SampleAssembler.assemble(
            request = SampleRequest(
                nowMillis = nowMillis,
                reason = reason,
                requestedIntervalMillis = requestedIntervalMillis,
                previousTimestampMillis = previousTimestampMillis,
                screenInteractive = interactiveProvider()
            ),
            battery = battery,
            thermal = thermal
        )
        previousTimestampMillis = nowMillis
        lastStoredSample = sample

        history.record(sample)
        evaluateAlerts(sample)
        // The ongoing notification is refreshed inside the same pass, so the
        // notification and the screen can never disagree about the last reading.
        refreshMonitoringNotification(sample)
        onSampleStored(sample)
        return sample
    }

    private suspend fun runLoop() {
        restorePersistedState()
        // The service must call startForeground within five seconds, so the very
        // first ongoing notification is built before any suspending read and
        // cannot know the persisted alert level. Refresh it as soon as that state
        // is restored, so an alert that survived a service restart is not shown as
        // an unremarkable reading for one whole sampling interval.
        refreshMonitoringNotification(lastStoredSample ?: history.latestSample.value)
        // A sample is taken immediately on start so the first screen the user
        // sees after enabling monitoring is never empty.
        sampleNow(SampleReason.SERVICE_START, SamplingPolicy.NORMAL_INTERVAL_MS)

        while (currentCoroutineContext().isActive) {
            val decision = nextDecision()
            _samplingDecision.value = decision
            delay(decision.intervalMillis)
            sampleNow(decision.reason, decision.intervalMillis)
        }
    }

    private fun nextDecision(): SamplingDecision {
        val latest = lastStoredSample
        val context = SamplingContext(
            alertLevel = _alertState.value.level,
            onExternalPower = latest?.battery?.onExternalPower ?: false,
            screenInteractive = interactiveProvider(),
            thermalStatus = thermalCollector.thermalStatus.value.valueOrNull,
            batteryPercent = latest?.battery?.batteryPercent?.valueOrNull
        )
        return SamplingPolicy.decide(context)
    }

    private suspend fun evaluateAlerts(sample: DeviceSample) {
        val rules = settings.rulesNow()
        val evaluation = AlertEngine.evaluate(
            previous = _alertState.value,
            rules = rules,
            input = AlertInput(
                temperatureC = sample.battery.temperatureC.valueOrNull,
                thermalStatus = sample.thermal.thermalStatus.valueOrNull,
                onExternalPower = sample.battery.onExternalPower
            ),
            nowMillis = sample.timestampMillis
        )
        _alertState.value = evaluation.state
        _alertExplanation.value = evaluation.explanation
        settings.saveAlertState(evaluation.state)

        for (action in evaluation.actions) {
            when (action) {
                is AlertAction.Notify -> {
                    history.recordEvent(
                        timestampMillis = sample.timestampMillis,
                        type = if (action.isReminder) ThermalEventType.ALERT_REMINDER else ThermalEventType.ALERT_RAISED,
                        level = action.level,
                        trigger = action.trigger,
                        temperatureC = action.temperatureC,
                        thermalStatus = action.thermalStatus,
                        detail = evaluation.explanation
                    )
                    publisher.publishAlert(action, rules, sample.battery.onExternalPower)
                }

                is AlertAction.Cleared -> {
                    history.recordEvent(
                        timestampMillis = sample.timestampMillis,
                        type = ThermalEventType.ALERT_CLEARED,
                        level = action.previousLevel,
                        temperatureC = action.temperatureC,
                        thermalStatus = action.thermalStatus,
                        detail = evaluation.explanation
                    )
                    publisher.publishClearedNotification(action, rules)
                }

                is AlertAction.AlertEnded -> {
                    // Deliberately no notification and no timeline row: the alert
                    // simply stopped being true, and ALERT_CLEARED is written when
                    // recovery is confirmed at NORMAL. What must not happen is the
                    // high-priority alert staying in the shade through recovery.
                    publisher.cancelAlertNotification()
                }
            }
        }
    }

    /**
     * [sample] is nullable because the first refresh of a session happens before
     * any sample has been taken in it; the publisher renders that as "waiting for
     * a reading" rather than as a zero.
     */
    private fun refreshMonitoringNotification(sample: DeviceSample?) {
        val notification = publisher.buildMonitoringNotification(
            sample = sample,
            alertLevel = _alertState.value.level,
            updating = true
        )
        publisher.updateMonitoringNotification(notification)
    }

}

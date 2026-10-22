package com.thermalsentinel.ui.live

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thermalsentinel.ThermalSentinelApp
import com.thermalsentinel.engine.alert.AlertLevel
import com.thermalsentinel.engine.alert.AlertState
import com.thermalsentinel.engine.data.MonitoringStopReason
import com.thermalsentinel.engine.domain.DeviceSample
import com.thermalsentinel.engine.service.MonitoringStartResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Monitoring control and live state, shared by the Dashboard and Settings.
 *
 * The two questions this exposes are deliberately kept separate, because they
 * have different answers after an OEM battery manager kills the service:
 *
 *  - [monitoringEnabled] — does the user want monitoring to run?
 *  - [serviceRunning] — is it running right now?
 *
 * When the first is true and the second is false the screen shows a resume
 * prompt with the recorded reason, instead of a switch that lies.
 */
class MonitoringViewModel(application: Application) : AndroidViewModel(application) {

    private val graph = (application as ThermalSentinelApp).graph

    val monitoringEnabled: StateFlow<Boolean> = graph.settings.monitoringEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val serviceRunning: StateFlow<Boolean> = graph.monitoring.isRunning

    val latestSample: StateFlow<DeviceSample?> = graph.history.latestSample

    val alertState: StateFlow<AlertState> = graph.settings.alertState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlertState()
    )

    val lastStopReason: StateFlow<MonitoringStopReason> = graph.settings.lastStopReason.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MonitoringStopReason.UNKNOWN
    )

    val lastStoppedAtMillis: StateFlow<Long?> = graph.settings.lastStoppedAtMillis.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val sessionCount: StateFlow<Int> = graph.settings.sessionCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun startMonitoring() = runControl {
        when (val result = graph.monitoring.start()) {
            MonitoringStartResult.Started -> _message.value = null
            is MonitoringStartResult.Rejected -> _message.value = result.message
        }
    }

    fun stopMonitoring() = runControl {
        graph.monitoring.stop()
        _message.value = "Monitoring stopped. Stored history is kept."
    }

    /** Resume only if the user previously enabled monitoring. */
    fun resumeIfRequested() = runControl {
        when (val result = graph.monitoring.resumeIfRequested()) {
            null, MonitoringStartResult.Started -> Unit
            is MonitoringStartResult.Rejected -> _message.value = result.message
        }
    }

    fun refreshNow() = runControl {
        graph.history.loadLatest()
        _message.value = "Reloaded the last stored reading."
    }

    /** Fences rapid repeated UI actions and always restores the busy state after a failure. */
    private fun runControl(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _message.value = "The monitoring action failed. Open Diagnostics and try again."
            } finally {
                _busy.value = false
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun alertLevel(): AlertLevel = alertState.value.level
}

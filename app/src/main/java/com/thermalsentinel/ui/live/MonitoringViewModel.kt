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

    fun startMonitoring() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            when (val result = graph.monitoring.start()) {
                is MonitoringStartResult.Started -> _message.value = null
                is MonitoringStartResult.Rejected -> _message.value = result.message
            }
            _busy.value = false
        }
    }

    fun stopMonitoring() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            graph.monitoring.stop()
            _message.value = "Monitoring stopped. Stored history is kept."
            _busy.value = false
        }
    }

    /**
     * Called from a visible screen. Only starts the service if the user had
     * enabled monitoring before, so the app never starts a persistent service on
     * its own initiative.
     */
    fun resumeIfRequested() {
        viewModelScope.launch {
            val result = graph.monitoring.resumeIfRequested() ?: return@launch
            if (result is MonitoringStartResult.Rejected) _message.value = result.message
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            graph.history.loadLatest()
            _message.value = "Reloaded the last stored reading."
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun alertLevel(): AlertLevel = alertState.value.level
}

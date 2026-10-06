package com.thermalsentinel.ui.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UiPreferencesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UiPreferencesRepository(application.applicationContext)

    /**
     * Null until DataStore emits its first value. Eagerly started so the
     * splash screen gate in MainActivity can observe the first emission even
     * before any Compose subscriber is attached.
     */
    val preferences: StateFlow<UiPreferences?> = repository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { repository.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { repository.setDynamicColor(value) }
    fun setAmoled(value: Boolean) = viewModelScope.launch { repository.setAmoled(value) }
    fun setAccent(value: AccentPreset) = viewModelScope.launch { repository.setAccent(value) }

    fun setWidgetStyle(value: WidgetStyle) = viewModelScope.launch { repository.setWidgetStyle(value) }
}

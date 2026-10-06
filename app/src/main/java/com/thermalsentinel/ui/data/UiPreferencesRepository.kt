package com.thermalsentinel.ui.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val TAG = "UiPreferencesRepository"

private val Context.uiDataStore by preferencesDataStore(name = "ui_preferences")

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

enum class AccentPreset(val storageValue: String, val label: String) {
    BLUE("blue", "Ocean"),
    VIOLET("violet", "Violet"),
    GREEN("green", "Emerald"),
    AMBER("amber", "Amber"),
    ROSE("rose", "Rose");

    companion object {
        fun fromStorage(value: String?): AccentPreset =
            entries.firstOrNull { it.storageValue == value } ?: BLUE
    }
}

data class UiPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoled: Boolean = false,
    val accent: AccentPreset = AccentPreset.BLUE,
    /**
     * Widget appearance. Persisted since the engine phase: the widget reads it,
     * so the Widgets screen is no longer a contract-only surface.
     */
    val widgetStyle: WidgetStyle = WidgetStyle.SYSTEM
)

class UiPreferencesRepository(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val amoled = booleanPreferencesKey("amoled")
        val accent = stringPreferencesKey("accent")
        val widgetStyle = stringPreferencesKey("widget_style")
    }

    /**
     * IOException is treated as "no persisted preferences yet" and falls back
     * to the documented defaults. Because UiPreferencesViewModel collects this
     * flow with SharingStarted.Eagerly, an uncaught read error would otherwise
     * crash the app during startup.
     */
    val preferences: Flow<UiPreferences> = context.uiDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { values ->
            UiPreferences(
                themeMode = ThemeMode.fromStorage(values[Keys.themeMode]),
                dynamicColor = values[Keys.dynamicColor] ?: true,
                amoled = values[Keys.amoled] ?: false,
                accent = AccentPreset.fromStorage(values[Keys.accent]),
                widgetStyle = WidgetStyle.fromStorage(values[Keys.widgetStyle])
            )
        }

    /**
     * Writes are also guarded. `edit` can throw IOException on a failing disk
     * or a broken DataStore file. Losing a preference write is preferable to
     * crashing the process from a ViewModel coroutine.
     */
    private suspend fun editSafely(block: (MutablePreferences) -> Unit) {
        try {
            context.uiDataStore.edit(block)
        } catch (io: IOException) {
            Log.w(TAG, "Failed to persist preference; keeping last written state", io)
        }
    }

    suspend fun setThemeMode(value: ThemeMode) =
        editSafely { it[Keys.themeMode] = value.storageValue }

    suspend fun setDynamicColor(value: Boolean) =
        editSafely { it[Keys.dynamicColor] = value }

    suspend fun setAmoled(value: Boolean) =
        editSafely { it[Keys.amoled] = value }

    suspend fun setAccent(value: AccentPreset) =
        editSafely { it[Keys.accent] = value.storageValue }

    suspend fun setWidgetStyle(value: WidgetStyle) =
        editSafely { it[Keys.widgetStyle] = value.storageValue }
}

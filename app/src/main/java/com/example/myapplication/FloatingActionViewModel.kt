package com.example.myapplication

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

data class FloatingActionState(
    val x: Int = 0,
    val y: Int = 100,
    val isMinimized: Boolean = false,
    val alpha: Float = 1.0f,
    val iconSize: Int = 48,
    val maxDockApps: Int = 4,
    val selectedApps: Set<String> = emptySet()
)

class FloatingActionViewModel(private val prefs: SharedPreferences) : ViewModel() {

    var uiState = mutableStateOf(FloatingActionState())
        private set

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
        when (key) {
            ComposeFloatingActionService.KEY_SELECTED_APPS,
            ComposeFloatingActionService.KEY_FLOATING_ALPHA,
            ComposeFloatingActionService.KEY_FLOATING_ICON_SIZE,
            ComposeFloatingActionService.KEY_MAX_DOCK_APPS -> {
                // Reload all state to ensure consistency
                loadInitialState()
            }
        }
    }

    init {
        loadInitialState()
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun loadInitialState() {
        uiState.value = FloatingActionState(
            x = prefs.getInt(ComposeFloatingActionService.KEY_FLOATING_X, 0),
            y = prefs.getInt(ComposeFloatingActionService.KEY_FLOATING_Y, 100),
            isMinimized = prefs.getBoolean(ComposeFloatingActionService.KEY_FLOATING_MINIMIZED_STATE, false),
            alpha = prefs.getFloat(ComposeFloatingActionService.KEY_FLOATING_ALPHA, 1.0f),
            iconSize = prefs.getInt(ComposeFloatingActionService.KEY_FLOATING_ICON_SIZE, 48),
            maxDockApps = prefs.getInt(ComposeFloatingActionService.KEY_MAX_DOCK_APPS, 4),
            selectedApps = prefs.getStringSet(ComposeFloatingActionService.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        )
    }

    fun updatePosition(x: Int, y: Int) {
        uiState.value = uiState.value.copy(x = x, y = y)
        prefs.edit()
            .putInt(ComposeFloatingActionService.KEY_FLOATING_X, x)
            .putInt(ComposeFloatingActionService.KEY_FLOATING_Y, y)
            .apply()
    }

    fun toggleMinimized() {
        val newMinimizedState = !uiState.value.isMinimized
        uiState.value = uiState.value.copy(isMinimized = newMinimizedState)
        prefs.edit().putBoolean(ComposeFloatingActionService.KEY_FLOATING_MINIMIZED_STATE, newMinimizedState).apply()
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}

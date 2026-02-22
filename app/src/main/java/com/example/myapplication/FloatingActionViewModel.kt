package com.example.myapplication

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _uiState = MutableStateFlow(FloatingActionState())
    val uiState: StateFlow<FloatingActionState> = _uiState.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
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
        _uiState.value = FloatingActionState(
            x = prefs.getInt(ComposeFloatingActionService.KEY_FLOATING_X, 0),
            y = prefs.getInt(ComposeFloatingActionService.KEY_FLOATING_Y, 100),
            isMinimized = prefs.getBoolean(ComposeFloatingActionService.KEY_FLOATING_MINIMIZED_STATE, false),
            alpha = prefs.getFloat(ComposeFloatingActionService.KEY_FLOATING_ALPHA, 1.0f),
            iconSize = prefs.getInt(ComposeFloatingActionService.KEY_FLOATING_ICON_SIZE, 48),
            maxDockApps = prefs.getInt(ComposeFloatingActionService.KEY_MAX_DOCK_APPS, 4),
            selectedApps = prefs.getStringSet(ComposeFloatingActionService.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        )
    }

    fun updatePosition(x: Int, y: Int, saveToPrefs: Boolean = true) {
        _uiState.value = _uiState.value.copy(x = x, y = y)
        if (saveToPrefs) {
            prefs.edit()
                .putInt(ComposeFloatingActionService.KEY_FLOATING_X, x)
                .putInt(ComposeFloatingActionService.KEY_FLOATING_Y, y)
                .apply()
        }
    }

    fun toggleMinimized() {
        val newMinimizedState = !_uiState.value.isMinimized
        _uiState.value = _uiState.value.copy(isMinimized = newMinimizedState)
        prefs.edit().putBoolean(ComposeFloatingActionService.KEY_FLOATING_MINIMIZED_STATE, newMinimizedState).apply()
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}

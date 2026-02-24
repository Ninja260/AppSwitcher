package com.example.myapplication

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

enum class SnapPosition { LEFT, RIGHT }

data class FloatingActionState(
    val snapPosition: SnapPosition = SnapPosition.LEFT,
    val y: Int = 100,
    val isMinimized: Boolean = false,
    val alpha: Float = 0.5f,
    val iconSize: Int = 48,
    val maxDockApps: Int = 4,
    val selectedApps: Set<String> = emptySet()
)

class FloatingActionViewModel(private val prefs: SharedPreferences) : ViewModel() {

    companion object {
        const val KEY_FLOATING_SNAP_POSITION = "floating_snap_position"
    }

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
        val snapPosition = if (prefs.getInt(KEY_FLOATING_SNAP_POSITION, 0) == 0) SnapPosition.LEFT else SnapPosition.RIGHT
        _uiState.value = FloatingActionState(
            snapPosition = snapPosition,
            y = prefs.getInt(ComposeFloatingActionService.KEY_FLOATING_Y, 100),
            isMinimized = prefs.getBoolean(ComposeFloatingActionService.KEY_FLOATING_MINIMIZED_STATE, false),
            alpha = prefs.getFloat(ComposeFloatingActionService.KEY_FLOATING_ALPHA, 0.5f),
            iconSize = prefs.getInt(ComposeFloatingActionService.KEY_FLOATING_ICON_SIZE, 48),
            maxDockApps = prefs.getInt(ComposeFloatingActionService.KEY_MAX_DOCK_APPS, 4),
            selectedApps = prefs.getStringSet(ComposeFloatingActionService.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        )
    }

    fun updatePosition(snapPosition: SnapPosition, y: Int, saveToPrefs: Boolean = true) {
        _uiState.value = _uiState.value.copy(snapPosition = snapPosition, y = y)
        if (saveToPrefs) {
            prefs.edit {
                putInt(KEY_FLOATING_SNAP_POSITION, if (snapPosition == SnapPosition.LEFT) 0 else 1)
                    .putInt(ComposeFloatingActionService.KEY_FLOATING_Y, y)
            }
        }
    }

    fun toggleMinimized() {
        val newMinimizedState = !_uiState.value.isMinimized
        _uiState.value = _uiState.value.copy(isMinimized = newMinimizedState)
        prefs.edit {
            putBoolean(
                ComposeFloatingActionService.KEY_FLOATING_MINIMIZED_STATE,
                newMinimizedState
            )
        }
    }

    fun removeApp(packageName: String) {
        val currentSelectedApps = _uiState.value.selectedApps.toMutableSet()
        if (currentSelectedApps.remove(packageName)) {
            prefs.edit {
                putStringSet(
                    ComposeFloatingActionService.KEY_SELECTED_APPS,
                    currentSelectedApps
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}

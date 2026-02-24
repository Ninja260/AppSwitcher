package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class HotkeyService : AccessibilityService() {

    private val tag = "HotkeyService"
    private var lastTriggerPressTime: Long = 0
    private val doublePressThreshold = 2000 // Milliseconds
    private var isWaitingForActionKey = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This will be used in Phase 3 for tracking window state changes.
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event)
        }

        // NOTE: For simplicity, settings are loaded on every key event. A more optimized
        // approach would be to load them once and refresh via a broadcast or service command.
        val prefs = getSharedPreferences(ComposeFloatingActionService.PREFS_NAME, Context.MODE_PRIVATE)
        val triggerKeyCode = prefs.getInt(ComposeFloatingActionService.KEY_HOTKEY_TRIGGER_MODIFIER, KeyEvent.KEYCODE_ALT_LEFT)
        val actionKey1 = prefs.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_1, KeyEvent.KEYCODE_1)
        val actionKey2 = prefs.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_2, KeyEvent.KEYCODE_2)
        val actionKey3 = prefs.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_3, KeyEvent.KEYCODE_3)
        val actionKey4 = prefs.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_4, KeyEvent.KEYCODE_4)
        val lastAppActionKey = prefs.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_LAST_APP, KeyEvent.KEYCODE_UNKNOWN)

        val currentKeyCode = event.keyCode

        // 1. Detect Trigger-Trigger double press
        if (isTriggerKey(currentKeyCode, triggerKeyCode)) {
            val currentTime = SystemClock.uptimeMillis()
            if (currentTime - lastTriggerPressTime < doublePressThreshold) {
                // Double press detected
                isWaitingForActionKey = true
                lastTriggerPressTime = 0 // Reset to prevent triple press from triggering
                Log.d(tag, "Trigger key double press detected. Waiting for an action key.")
                return true // Consume the event
            } else {
                // This is the first press
                lastTriggerPressTime = currentTime
            }
            // Allow the first press to be handled by the system (e.g., for standard Alt-Tab)
            return super.onKeyEvent(event)
        }

        // 2. If double-press was detected, look for an action key
        if (isWaitingForActionKey) {
            val appIndex = when (currentKeyCode) {
                actionKey1 -> 0
                actionKey2 -> 1
                actionKey3 -> 2
                actionKey4 -> 3
                else -> -1
            }

            // Always reset the waiting state after the next key press
            isWaitingForActionKey = false

            if (appIndex != -1) {
                Log.d(tag, "Hotkey sequence detected for app ${appIndex + 1}. Launching.")
                launchApp(appIndex)
                return true // Consume the action key event
            }
            // TODO: Add logic for lastAppActionKey
        }

        // If not part of our hotkey sequence, pass the event on.
        return super.onKeyEvent(event)
    }

    // Helper to check if the pressed key matches the configured trigger key, handling left/right variants.
    private fun isTriggerKey(pressedKeyCode: Int, configuredKeyCode: Int): Boolean {
        return when (configuredKeyCode) {
            KeyEvent.KEYCODE_ALT_LEFT -> pressedKeyCode == KeyEvent.KEYCODE_ALT_LEFT || pressedKeyCode == KeyEvent.KEYCODE_ALT_RIGHT
            KeyEvent.KEYCODE_CTRL_LEFT -> pressedKeyCode == KeyEvent.KEYCODE_CTRL_LEFT || pressedKeyCode == KeyEvent.KEYCODE_CTRL_RIGHT
            KeyEvent.KEYCODE_SHIFT_LEFT -> pressedKeyCode == KeyEvent.KEYCODE_SHIFT_LEFT || pressedKeyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
            KeyEvent.KEYCODE_META_LEFT -> pressedKeyCode == KeyEvent.KEYCODE_META_LEFT || pressedKeyCode == KeyEvent.KEYCODE_META_RIGHT
            else -> pressedKeyCode == configuredKeyCode // Should not happen with current UI
        }
    }

    private fun launchApp(index: Int) {
        Log.d(tag, "launchApp called for index: $index")
        val prefs = getSharedPreferences(ComposeFloatingActionService.PREFS_NAME, Context.MODE_PRIVATE)
        val selectedAppsSet = prefs.getStringSet(ComposeFloatingActionService.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        val selectedApps = selectedAppsSet.toList().sorted() // Sort alphabetically for predictable order
        Log.d(tag, "Currently selected apps: $selectedApps")

        if (index < selectedApps.size) {
            val packageName = selectedApps[index]
            Log.d(tag, "Attempting to launch app $index: $packageName")
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Log.d(tag, "Launch intent sent for $packageName")
            } else {
                Log.w(tag, "Could not get launch intent for package: $packageName")
            }
        } else {
            Log.d(tag, "No app configured for index $index. Only ${selectedApps.size} apps are selected.")
        }
    }

    override fun onInterrupt() {
        Log.d(tag, "Service interrupted.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(tag, "HotkeyService connected. Programmatically setting flags.")
        val info = serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        this.serviceInfo = info
    }
}

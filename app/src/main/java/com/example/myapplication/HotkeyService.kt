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
        // If the main service is disabled, do nothing.
        if (!ComposeFloatingActionService.isServiceRunning) {
            return
        }
        // This service no longer needs to track window state changes.
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // If the main service is disabled, pass all key events through without processing.
        if (!ComposeFloatingActionService.isServiceRunning) {
            return super.onKeyEvent(event)
        }

        if (event == null || event.action != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event)
        }

        val prefs = getSharedPreferences(ComposeFloatingActionService.PREFS_NAME, Context.MODE_PRIVATE)
        val triggerKeyCode = prefs.getInt(ComposeFloatingActionService.KEY_HOTKEY_TRIGGER_MODIFIER, KeyEvent.KEYCODE_ALT_LEFT)
        val actionKey1 = prefs.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_1, KeyEvent.KEYCODE_J)
        val actionKey2 = prefs.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_2, KeyEvent.KEYCODE_K)
        val actionKey3 = prefs.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_3, KeyEvent.KEYCODE_L)
        val actionKey4 = prefs.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_4, KeyEvent.KEYCODE_SEMICOLON)

        val currentKeyCode = event.keyCode

        // 1. Detect Trigger-Trigger double press
        if (isTriggerKey(currentKeyCode, triggerKeyCode)) {
            val currentTime = SystemClock.uptimeMillis()
            if (currentTime - lastTriggerPressTime < doublePressThreshold) {
                isWaitingForActionKey = true
                lastTriggerPressTime = 0
                Log.d(tag, "Trigger key double press detected. Waiting for an action key.")
                return true
            } else {
                lastTriggerPressTime = currentTime
            }
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

            isWaitingForActionKey = false // Reset state after this key press

            if (appIndex != -1) {
                Log.d(tag, "Hotkey sequence detected for app ${appIndex + 1}. Launching.")
                launchAppByIndex(appIndex)
                return true
            }
        }

        return super.onKeyEvent(event)
    }

    // Helper to check if the pressed key matches the configured trigger key, handling left/right variants.
    private fun isTriggerKey(pressedKeyCode: Int, configuredKeyCode: Int): Boolean {
        return when (configuredKeyCode) {
            KeyEvent.KEYCODE_ALT_LEFT -> pressedKeyCode == KeyEvent.KEYCODE_ALT_LEFT || pressedKeyCode == KeyEvent.KEYCODE_ALT_RIGHT
            KeyEvent.KEYCODE_CTRL_LEFT -> pressedKeyCode == KeyEvent.KEYCODE_CTRL_LEFT || pressedKeyCode == KeyEvent.KEYCODE_CTRL_RIGHT
            KeyEvent.KEYCODE_SHIFT_LEFT -> pressedKeyCode == KeyEvent.KEYCODE_SHIFT_LEFT || pressedKeyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
            KeyEvent.KEYCODE_META_LEFT -> pressedKeyCode == KeyEvent.KEYCODE_META_LEFT || pressedKeyCode == KeyEvent.KEYCODE_META_RIGHT
            else -> pressedKeyCode == configuredKeyCode
        }
    }

    private fun launchAppByIndex(index: Int) {
        Log.d(tag, "launchApp called for index: $index")
        val prefs = getSharedPreferences(ComposeFloatingActionService.PREFS_NAME, Context.MODE_PRIVATE)
        val selectedAppsSet = prefs.getStringSet(ComposeFloatingActionService.KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        val selectedApps = selectedAppsSet.toList().sorted()
        Log.d(tag, "Currently selected apps: $selectedApps")

        if (index < selectedApps.size) {
            launchAppByPackageName(selectedApps[index])
        } else {
            Log.d(tag, "No app configured for index $index. Only ${selectedApps.size} apps are selected.")
        }
    }

    private fun launchAppByPackageName(packageName: String) {
        Log.d(tag, "Attempting to launch app: $packageName")
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            Log.d(tag, "Launch intent sent for $packageName")
        } else {
            Log.w(tag, "Could not get launch intent for package: $packageName")
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

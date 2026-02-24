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
    private var lastAltPressTime: Long = 0
    private val doublePressThreshold = 2000 // Milliseconds
    private var isWaitingForNumber = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This will be used in Phase 3 for tracking window state changes.
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        val isKeyDown = event.action == KeyEvent.ACTION_DOWN
        val keyCode = event.keyCode

        // We only care about key down events for triggering actions
        if (!isKeyDown) {
            return super.onKeyEvent(event)
        }

        Log.d(tag, "onKeyEvent: ${KeyEvent.keyCodeToString(keyCode)}, Action: DOWN")

        // 1. Detect Alt-Alt double press
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            val currentTime = SystemClock.uptimeMillis()
            if (currentTime - lastAltPressTime < doublePressThreshold) {
                // Double press detected
                isWaitingForNumber = true
                lastAltPressTime = 0 // Reset to prevent triple press from triggering
                Log.d(tag, "Alt double press detected. Waiting for a number key.")
                // We consume the second Alt press to avoid it interfering with anything
                return true
            } else {
                // This is the first Alt press (or a press after a long delay)
                lastAltPressTime = currentTime
                // We don't set isWaitingForNumber to false here, because another key might reset it.
            }
            // We don't consume the first Alt press, allowing it to function normally
            return super.onKeyEvent(event)
        }

        // 2. If double-press was detected, look for a number key
        if (isWaitingForNumber) {
            val appIndex = when (keyCode) {
                KeyEvent.KEYCODE_1 -> 0
                KeyEvent.KEYCODE_2 -> 1
                KeyEvent.KEYCODE_3 -> 2
                KeyEvent.KEYCODE_4 -> 3
                else -> -1
            }

            // Always reset the waiting state after the next key press
            isWaitingForNumber = false

            if (appIndex != -1) {
                Log.d(tag, "Hotkey 'Alt, Alt, ${appIndex + 1}' detected. Launching app.")
                launchApp(appIndex)
                // Consume the number key event because it was part of our hotkey
                return true
            }
            // If it wasn't a number key, we fall through and let the event be handled by the system
            // because we already reset the isWaitingForNumber flag.
        }

        // If not part of our hotkey sequence, pass the event on.
        return super.onKeyEvent(event)
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
        // Programmatically setting the flags is a more robust way to ensure the service gets key events.
        val info = serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        this.serviceInfo = info
    }
}

package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class HotkeyService : AccessibilityService() {

    private val tag = "HotkeyService"
    private var isSuperKeyPressed = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // This will be used in Phase 3 for tracking window state changes.
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        val isKeyDown = event.action == KeyEvent.ACTION_DOWN
        Log.d(tag, "onKeyEvent: ${KeyEvent.keyCodeToString(event.keyCode)}, Action: ${if (isKeyDown) "DOWN" else "UP"}")

        // Track the state of the Super key (Meta key)
        if (event.keyCode == KeyEvent.KEYCODE_META_LEFT || event.keyCode == KeyEvent.KEYCODE_META_RIGHT) {
            isSuperKeyPressed = isKeyDown
            Log.d(tag, "Super key state: $isSuperKeyPressed")
        }

        // If Super key is pressed, check for number keys
        if (isSuperKeyPressed && isKeyDown) {
            // Guard to ensure we don't act on the Super key press itself, only on other keys pressed WHILE it's held down.
            if (event.keyCode == KeyEvent.KEYCODE_META_LEFT || event.keyCode == KeyEvent.KEYCODE_META_RIGHT) {
                return super.onKeyEvent(event)
            }

            val appIndex = when (event.keyCode) {
                KeyEvent.KEYCODE_1 -> 0
                KeyEvent.KEYCODE_2 -> 1
                KeyEvent.KEYCODE_3 -> 2
                KeyEvent.KEYCODE_4 -> 3
                else -> -1
            }

            if (appIndex != -1) {
                Log.d(tag, "Hotkey 'Super + ${appIndex + 1}' detected! Launching app at index: $appIndex")
                launchApp(appIndex)
                return true // Consume the event
            }
        }

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

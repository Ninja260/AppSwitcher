package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.util.LinkedList

class HotkeyService : AccessibilityService() {

    private val tag = "HotkeyService"
    private var lastTriggerPressTime: Long = 0
    private val doublePressThreshold = 2000 // Milliseconds
    private var isWaitingForActionKey = false

    // In-memory list to track the last two unique foreground apps
    private val recentApps = LinkedList<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // Filter out events from the launcher, this app, or invalid packages
            if (packageName == "com.example.myapplication" || isLauncher(packageName)) {
                return
            }
            
            // If the list is empty or the new app is different from the most recent one
            if (recentApps.isEmpty() || recentApps.first != packageName) {
                recentApps.addFirst(packageName)
                Log.d(tag, "App switched to: $packageName. Recent apps list: $recentApps")
            }

            // Keep the list size at a maximum of 2
            while (recentApps.size > 2) {
                recentApps.removeLast()
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) {
            return super.onKeyEvent(event)
        }

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
            } else if (currentKeyCode == lastAppActionKey && currentKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
                Log.d(tag, "Hotkey sequence for last app detected.")
                switchToLastApp()
                return true
            }
        }

        return super.onKeyEvent(event)
    }

    private fun isLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun switchToLastApp() {
        if (recentApps.size > 1) {
            val lastAppPackage = recentApps[1] // The second element is the "last" app
            Log.d(tag, "Switching to last app: $lastAppPackage")
            launchAppByPackageName(lastAppPackage)
        } else {
            Log.d(tag, "Not enough recent apps to switch.")
        }
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
        // We also need to request window state change events
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        this.serviceInfo = info
    }
}

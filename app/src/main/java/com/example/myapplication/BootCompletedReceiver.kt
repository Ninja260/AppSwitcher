package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {

    private val tagName = "BootCompletedReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(tagName, "Boot completed intent received.")
            val prefs = context.getSharedPreferences(ComposeFloatingActionService.PREFS_NAME, Context.MODE_PRIVATE)
            val isServiceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, false)

            Log.d(tagName, "Service enabled preference is: $isServiceEnabled")

            if (isServiceEnabled) {
                Log.d(tagName, "Service is enabled, attempting to start ComposeFloatingActionService.")
                val serviceIntent = Intent(context, ComposeFloatingActionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.d(tagName, "Service is not enabled, doing nothing.")
            }
        }
    }

    companion object {
        const val KEY_SERVICE_ENABLED = "service_enabled"
    }
}

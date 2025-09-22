package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log // Added import
import androidx.core.app.NotificationCompat

class FloatingActionService : Service() {

    private val CHANNEL_ID = "FloatingActionServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "FloatingActionService" // Added TAG for logging

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called")
        // We don't provide binding, so return null
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        // Intent to launch the MainActivity when the notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        Log.d(TAG, "Building notification...")
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Switcher Active")
            .setContentText("Floating action button is running.")
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your app's icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Explicitly set priority
            .build()

        if (notification == null) {
            Log.e(TAG, "Notification object is null after building!")
        } else {
            Log.d(TAG, "Notification built successfully. Small icon ID: " + R.mipmap.ic_launcher)
        }

        try {
            Log.d(TAG, "Calling startForeground...")
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "startForeground called successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error calling startForeground: " + e.message, e)
        }

        // Service logic
        // For now, it just starts and stays running.
        // We'll add the floating action view logic later.

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
        // Cleanup code for the service
        // May need to stop foreground if it wasn't already (e.g. stopSelf() was called)
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "createNotificationChannel called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Floating Action Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            if (manager == null) {
                Log.e(TAG, "NotificationManager is null!")
                return
            }
            manager.createNotificationChannel(serviceChannel)
            Log.d(TAG, "Notification channel created/updated.")
        } else {
            Log.d(TAG, "Skipping notification channel creation (below Android O).")
        }
    }
}

package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button // Added for the button
import androidx.core.app.NotificationCompat

class FloatingActionService : Service() {

    private val CHANNEL_ID = "FloatingActionServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "FloatingActionService"

    private lateinit var windowManager: WindowManager
    private var floatingButton: Button? = null // View for the floating button

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        createNotificationChannel()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Create the floating button
        floatingButton = Button(this).apply {
            text = "FAB" // Simple text for the button
            setBackgroundColor(Color.BLUE) // Basic styling
            setTextColor(Color.WHITE)
            // We can set an OnClickListener later if needed
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE // For older versions
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START // Initial position: top-left
            x = 100 // Initial x offset
            y = 100 // Initial y offset
        }

        try {
            if (floatingButton?.windowToken == null) {
                 Log.d(TAG, "Adding floating button to WindowManager")
                windowManager.addView(floatingButton, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding view to WindowManager: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
        floatingButton?.let {
            try {
                if (it.windowToken != null) { // Check if the view is still attached
                    Log.d(TAG, "Removing floating button from WindowManager")
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view from WindowManager: ${e.message}", e)
            }
            floatingButton = null
        }
    }

    private fun createNotificationChannel() {
        // ... (rest of the method remains the same)
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

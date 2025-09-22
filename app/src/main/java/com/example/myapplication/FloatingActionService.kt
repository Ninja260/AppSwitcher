package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager // Added
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable // Added
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View // Added
import android.view.WindowManager
import android.widget.ImageView // Added
import android.widget.LinearLayout // Added
import androidx.core.app.NotificationCompat

// SharedPreferences constants (mirrored from SettingsActivity)
private const val PREFS_NAME = "app_switcher_prefs"
private const val KEY_SELECTED_APPS = "selected_app_packages"

class FloatingActionService : Service() {

    private val CHANNEL_ID = "FloatingActionServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "FloatingActionService"

    private lateinit var windowManager: WindowManager
    // private var floatingButton: Button? = null // Replaced by floatingView
    private var floatingView: View? = null // Changed to View to hold LinearLayout

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        createNotificationChannel()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedAppPackages = prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        Log.d(TAG, "Selected apps loaded for UI: $selectedAppPackages")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val packageManager = applicationContext.packageManager

        val iconSize = (48 * resources.displayMetrics.density).toInt() // Example icon size (48dp)

        // Create a LinearLayout to hold the app icons
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.parseColor("#80000000")) // Semi-transparent background
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx()) // Add some padding
        }

        if (selectedAppPackages.isEmpty()) {
            Log.d(TAG, "No apps selected to display in floating view.")
            // Optionally, you could add a TextView here indicating no apps are selected
            // or handle this case by not showing the view at all.
            // For now, an empty layout will be shown if no apps are selected.
        } else {
            selectedAppPackages.forEach { packageName ->
                try {
                    val appIcon: Drawable = packageManager.getApplicationIcon(packageName)
                    val imageView = ImageView(this).apply {
                        setImageDrawable(appIcon)
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                            marginEnd = 8.dpToPx() // Add some margin between icons
                        }
                        // TODO: Set an OnClickListener in task 5.1 to launch the app
                        contentDescription = packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(packageName, 0)
                        ).toString()
                    }
                    linearLayout.addView(imageView)
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e(TAG, "App not found: $packageName", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading icon for $packageName", e)
                }
            }
        }
        floatingView = linearLayout // Assign the LinearLayout to floatingView

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        try {
            // Check if floatingView is null and if it's already added.
            // floatingView might be non-null but already part of a window if service restarts.
            if (floatingView?.windowToken == null) {
                Log.d(TAG, "Adding floating view to WindowManager")
                windowManager.addView(floatingView, params)
            } else {
                Log.d(TAG, "Floating view already added or has a window token.")
                 // If you want to update the view if it's already there, you'd call
                 // windowManager.updateViewLayout(floatingView, params)
                 // However, for adding icons initially, addView is correct if it's not present.
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding/updating view in WindowManager: ${e.message}", e)
        }
    }

    // Helper extension function to convert dp to pixels
    fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Switcher Active")
            .setContentText("Floating action is running.")
            .setSmallIcon(R.mipmap.ic_launcher) // Make sure this icon exists
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling startForeground: " + e.message, e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
        floatingView?.let {
            try {
                // Check if the view is still attached to a window
                if (it.windowToken != null) {
                    Log.d(TAG, "Removing floating view from WindowManager")
                    windowManager.removeView(it)
                } else {
                    Log.d(TAG, "Floating view not attached to a window, no need to remove.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view from WindowManager: ${e.message}", e)
            }
            floatingView = null
        }
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
            manager?.createNotificationChannel(serviceChannel)
            Log.d(TAG, "Notification channel created/updated.")
        } else {
            Log.d(TAG, "Skipping notification channel creation (below Android O).")
        }
    }
}

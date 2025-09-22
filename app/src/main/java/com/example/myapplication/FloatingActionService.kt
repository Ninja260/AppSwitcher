package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException // Added
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast // Added for user feedback on launch failure
import androidx.core.app.NotificationCompat

// SharedPreferences constants (mirrored from SettingsActivity)
private const val PREFS_NAME = "app_switcher_prefs"
private const val KEY_SELECTED_APPS = "selected_app_packages"

class FloatingActionService : Service() {

    private val CHANNEL_ID = "FloatingActionServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "FloatingActionService"

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

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
        val localPackageManager = applicationContext.packageManager // Renamed for clarity

        val iconSize = (48 * resources.displayMetrics.density).toInt()

        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }

        if (selectedAppPackages.isEmpty()) {
            Log.d(TAG, "No apps selected to display in floating view.")
        } else {
            selectedAppPackages.forEach { packageName ->
                try {
                    val appIcon: Drawable = localPackageManager.getApplicationIcon(packageName)
                    val appLabel = localPackageManager.getApplicationLabel(
                        localPackageManager.getApplicationInfo(packageName, 0)
                    ).toString()

                    val imageView = ImageView(this).apply {
                        setImageDrawable(appIcon)
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                            marginEnd = 8.dpToPx()
                        }
                        contentDescription = appLabel
                        
                        // Set OnClickListener to launch the app
                        setOnClickListener {
                            Log.d(TAG, "Icon tapped for $packageName")
                            try {
                                val launchIntent = localPackageManager.getLaunchIntentForPackage(packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(launchIntent)
                                    Log.i(TAG, "Launched $packageName successfully.")
                                    // Optional: Close the floating view after launch, or keep it open
                                    // For now, it stays open.
                                } else {
                                    Log.e(TAG, "Could not get launch intent for $packageName")
                                    Toast.makeText(applicationContext, "Cannot open $appLabel", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: ActivityNotFoundException) {
                                Log.e(TAG, "Activity not found for $packageName. App might be uninstalled.", e)
                                Toast.makeText(applicationContext, "$appLabel not found.", Toast.LENGTH_SHORT).show()
                                // TODO: Optionally, remove this app from SharedPreferences if it's confirmed uninstalled
                            } catch (e: Exception) {
                                Log.e(TAG, "Error launching $packageName: ${e.message}", e)
                                Toast.makeText(applicationContext, "Error opening $appLabel", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    linearLayout.addView(imageView)
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e(TAG, "App not found during setup: $packageName", e)
                    // This app won't be added to the floating view
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading icon or info for $packageName during setup", e)
                }
            }
        }
        floatingView = linearLayout

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
            if (floatingView?.windowToken == null) {
                Log.d(TAG, "Adding floating view to WindowManager")
                windowManager.addView(floatingView, params)
            } else {
                Log.d(TAG, "Floating view already added or has a window token.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding/updating view in WindowManager: ${e.message}", e)
        }
    }

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
            .setSmallIcon(R.mipmap.ic_launcher)
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

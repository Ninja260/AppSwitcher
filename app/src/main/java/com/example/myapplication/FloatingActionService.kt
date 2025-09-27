package com.example.myapplication

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
// Removed: import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
// Removed: import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
// Removed: import androidx.localbroadcastmanager.content.LocalBroadcastManager

// Moved KEY_FLOATING_X and KEY_FLOATING_Y to companion object for potential future shared use if needed
// though they are currently only used internally by the service.
// private const val KEY_FLOATING_X = "floating_x" // Now in companion
// private const val KEY_FLOATING_Y = "floating_y" // Now in companion

class FloatingActionService : Service() {

    companion object {
        const val ACTION_REFRESH_FLOATING_VIEW = "com.example.myapplication.ACTION_REFRESH_FLOATING_VIEW"
        const val PREFS_NAME = "app_switcher_prefs"
        const val KEY_SELECTED_APPS = "selected_app_packages"
        const val KEY_FLOATING_X = "floating_x"
        const val KEY_FLOATING_Y = "floating_y"
    }

    private val CHANNEL_ID = "FloatingActionServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "FloatingActionService"

    private lateinit var windowManager: WindowManager
    private var floatingView: DraggableLinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        createNotificationChannel()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_FLOATING_X, 100)
            y = prefs.getInt(KEY_FLOATING_Y, 100)
        }

        floatingView = DraggableLinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            // Initial padding is set here, refreshAppIconsView will adjust it dynamically
            val initialPadding = 8.dpToPx()
            setPadding(initialPadding, initialPadding, initialPadding, initialPadding)
            setWindowManagerParams(windowManager, params, prefs, KEY_FLOATING_X, KEY_FLOATING_Y)
        }

        try {
            if (floatingView?.windowToken == null) {
                Log.d(TAG, "Adding floating view to WindowManager with params: x=${params.x}, y=${params.y}")
                windowManager.addView(floatingView, params)
            } else {
                Log.d(TAG, "Floating view already has window token, updating layout. Params: x=${params.x}, y=${params.y}")
                windowManager.updateViewLayout(floatingView, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding/updating view in WindowManager: ${e.message}", e)
            floatingView = null
        }
        refreshAppIconsView() // Initial refresh
    }

    private fun removeAppFromSwitcher(packageName: String, appLabel: String) {
        Log.i(TAG, "Removing $appLabel ($packageName) from switcher due to launch failure.")
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val currentSelectedApps = prefs.getStringSet(KEY_SELECTED_APPS, HashSet()) ?: HashSet()
        val newSelectedApps = HashSet(currentSelectedApps)

        if (newSelectedApps.remove(packageName)) {
            editor.putStringSet(KEY_SELECTED_APPS, newSelectedApps).apply()
            Log.i(TAG, "$packageName removed from SharedPreferences.")
            Toast.makeText(applicationContext, "$appLabel removed from switcher.", Toast.LENGTH_LONG).show()
            refreshAppIconsView()
        } else {
            Log.w(TAG, "$packageName was already removed or not found in SharedPreferences.")
            refreshAppIconsView()
        }
    }

    private fun refreshAppIconsView() {
        val currentFloatingView = floatingView ?: run {
            Log.e(TAG, "FloatingView is null, cannot refresh icons.")
            return
        }
        currentFloatingView.removeAllViews()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedAppPackagesSet = prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        Log.d(TAG, "Refreshing icons. Selected apps: $selectedAppPackagesSet")

        // Dynamically set padding based on whether apps are selected
        if (selectedAppPackagesSet.isEmpty()) {
            Log.d(TAG, "No apps selected, removing padding from floating view.")
            currentFloatingView.setPadding(0, 0, 0, 0) // Remove padding
        } else {
            Log.d(TAG, "Apps selected, ensuring default padding for floating view.")
            val paddingInPx = 8.dpToPx()
            currentFloatingView.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx) // Restore/set default padding
        }

        if (selectedAppPackagesSet.isEmpty()) {
            Log.d(TAG, "No apps selected to display in floating view.")
            return // Exit if no apps, padding has been handled above
        }

        val localPackageManager = applicationContext.packageManager
        val iconSize = (48 * resources.displayMetrics.density).toInt()
        val selectedAppPackagesList = selectedAppPackagesSet.toList()

        selectedAppPackagesList.forEachIndexed { index, packageName ->
            try {
                val appInfo = localPackageManager.getApplicationInfo(packageName, 0)
                val appLabel = localPackageManager.getApplicationLabel(appInfo).toString()
                val appIcon: Drawable = localPackageManager.getApplicationIcon(packageName)

                val imageView = ImageView(this).apply {
                    setImageDrawable(appIcon)
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        if (index < selectedAppPackagesList.lastIndex) {
                            bottomMargin = 8.dpToPx()
                        }
                    }
                    contentDescription = appLabel
                    isClickable = true
                    isFocusable = true

                    setOnClickListener {
                        Log.d(TAG, "Icon tapped for $packageName ($appLabel)")
                        try {
                            val launchIntent = localPackageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(launchIntent)
                                Log.i(TAG, "Launched $packageName successfully.")
                            } else {
                                Log.e(TAG, "Could not get launch intent for $packageName.")
                                removeAppFromSwitcher(packageName, appLabel)
                            }
                        } catch (e: ActivityNotFoundException) {
                            Log.e(TAG, "Activity not found for $packageName.", e)
                            removeAppFromSwitcher(packageName, appLabel)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error launching $packageName: ${e.message}", e)
                            removeAppFromSwitcher(packageName, appLabel)
                        }
                    }
                }
                currentFloatingView.addView(imageView)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "App not found during icon refresh: $packageName. Removing.", e)
                removeAppFromSwitcher(packageName, packageName) // Pass packageName as label too for consistency in removal
            } catch (e: Exception) {
                Log.e(TAG, "Error loading icon/info for $packageName during icon refresh: ${e.message}", e)
                removeAppFromSwitcher(packageName, packageName) // Pass packageName as label too
            }
        }
    }

    fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called. Intent Action: ${intent?.action}, Flags: $flags, StartId: $startId")

        if (intent?.action == ACTION_REFRESH_FLOATING_VIEW) {
            Log.d(TAG, "Received refresh action in onStartCommand. Refreshing icons.")
            if (floatingView != null && floatingView?.windowToken != null) {
                refreshAppIconsView()
            } else {
                Log.w(TAG, "Refresh action received but view is not ready. Refresh will happen on view setup.")
            }
        }

        if (floatingView == null) {
            Log.w(TAG, "onStartCommand: floatingView is null, service proceeds with onCreate logic for setup (which includes refresh).")
        } else if (floatingView?.windowToken == null) {
            Log.w(TAG, "onStartCommand: floatingView exists but not attached to window. Attempting to re-add.")
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            params.x = prefs.getInt(KEY_FLOATING_X, params.x)
            params.y = prefs.getInt(KEY_FLOATING_Y, params.y)
            try {
                if (floatingView?.parent == null) {
                    Log.d(TAG, "Re-adding floating view in onStartCommand. Pos: x=${params.x}, y=${params.y}")
                    windowManager.addView(floatingView, params)
                } else {
                    Log.d(TAG, "Floating view has parent, updating layout in onStartCommand. Pos: x=${params.x}, y=${params.y}")
                    windowManager.updateViewLayout(floatingView, params)
                }
                if (intent?.action != ACTION_REFRESH_FLOATING_VIEW) { // Avoid double refresh if action was already refresh
                    refreshAppIconsView()
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "View already added or other issue in onStartCommand re-add: ${e.message}")
                try {
                    windowManager.updateViewLayout(floatingView, params)
                } catch (updE: Exception) {
                    Log.e(TAG, "Failed to update view layout after re-add attempt failed: ${updE.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error re-adding/updating view in onStartCommand: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "onStartCommand: Floating view already initialized and attached.")
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Switcher Active")
            .setContentText("Floating action is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "startForeground called successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error calling startForeground: ${e.message}", e)
            // Consider stopping the service if startForeground fails criticaly,
            // or at least ensure the floating view isn't shown.
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view from WindowManager in onDestroy: ${e.message}", e)
            }
            floatingView = null
        }
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "createNotificationChannel called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Floating Action Service Channel"
            val descriptionText = "Channel for Floating Action Service notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created/updated.")
        }
    }
}

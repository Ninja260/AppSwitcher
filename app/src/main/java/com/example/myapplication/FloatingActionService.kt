package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
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
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat

private const val PREFS_NAME = "app_switcher_prefs"
private const val KEY_SELECTED_APPS = "selected_app_packages"

class FloatingActionService : Service() {

    private val CHANNEL_ID = "FloatingActionServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "FloatingActionService"

    private lateinit var windowManager: WindowManager
    private var floatingView: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate called")
        createNotificationChannel()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }

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
        refreshAppIconsView()
    }

    private fun removeAppFromSwitcher(packageName: String, appLabel: String) {
        Log.i(TAG, "Removing $appLabel ($packageName) from switcher due to launch failure.")
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val currentSelectedApps = prefs.getStringSet(KEY_SELECTED_APPS, HashSet()) ?: HashSet()
        val newSelectedApps = HashSet(currentSelectedApps) // Create a mutable copy

        if (newSelectedApps.remove(packageName)) {
            editor.putStringSet(KEY_SELECTED_APPS, newSelectedApps).apply()
            Log.i(TAG, "$packageName removed from SharedPreferences.")
            Toast.makeText(applicationContext, "$appLabel removed from switcher.", Toast.LENGTH_LONG).show()
            refreshAppIconsView() // Refresh the view to reflect removal
        } else {
            Log.w(TAG, "$packageName was already removed or not found in SharedPreferences during removal attempt.")
            // No toast here as it might be confusing if the user didn'''t initiate the removal.
            // Still refresh, in case the view is somehow out of sync.
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
        val selectedAppPackages = prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        Log.d(TAG, "Refreshing icons. Selected apps: $selectedAppPackages")

        if (selectedAppPackages.isEmpty()) {
            Log.d(TAG, "No apps selected to display in floating view after refresh.")
            return
        }

        val localPackageManager = applicationContext.packageManager
        val iconSize = (48 * resources.displayMetrics.density).toInt()

        selectedAppPackages.forEach { packageName ->
            try {
                // It'''s important to fetch appLabel here, before the launch attempt,
                // as we need it for the removeAppFromSwitcher function even if getApplicationIcon fails.
                // However, getApplicationInfo is needed for the label. If this fails, the icon would too.
                // Let'''s assume if getApplicationIcon works, getApplicationInfo will likely work.
                // If getApplicationIcon itself fails, we hit the outer catch.
                val appInfo = localPackageManager.getApplicationInfo(packageName, 0)
                val appLabel = localPackageManager.getApplicationLabel(appInfo).toString()
                val appIcon: Drawable = localPackageManager.getApplicationIcon(packageName)


                val imageView = ImageView(this).apply {
                    setImageDrawable(appIcon)
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        marginEnd = 8.dpToPx()
                    }
                    contentDescription = appLabel

                    setOnClickListener {
                        Log.d(TAG, "Icon tapped for $packageName ($appLabel)")
                        try {
                            val launchIntent = localPackageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(launchIntent)
                                Log.i(TAG, "Launched $packageName successfully.")
                            } else {
                                Log.e(TAG, "Could not get launch intent for $packageName. Removing from switcher.")
                                removeAppFromSwitcher(packageName, appLabel)
                            }
                        } catch (e: ActivityNotFoundException) {
                            Log.e(TAG, "Activity not found for $packageName. Removing from switcher.", e)
                            removeAppFromSwitcher(packageName, appLabel)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error launching $packageName. Removing from switcher. Error: ${e.message}", e)
                            removeAppFromSwitcher(packageName, appLabel)
                        }
                    }
                }
                currentFloatingView.addView(imageView)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "App not found during icon refresh: $packageName. It might have been uninstalled.", e)
                // If app is not found even when trying to get its icon/label for display, remove it.
                // We need a label, but if app info itself fails, we use packageName as a fallback for the toast.
                val appLabelFallback = packageName
                removeAppFromSwitcher(packageName, appLabelFallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading icon or info for $packageName during icon refresh. Error: ${e.message}", e)
                // Generic error loading icon/info. Consider removing.
                val appLabelFallback = packageName // Fallback label
                removeAppFromSwitcher(packageName, appLabelFallback)
            }
        }
    }

    fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
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
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Floating Action Service Channel", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}

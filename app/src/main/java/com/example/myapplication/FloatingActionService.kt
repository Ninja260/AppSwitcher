package com.example.myapplication

import android.annotation.SuppressLint
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
// Removed MotionEvent and View imports as they are now handled in DraggableLinearLayout
import android.view.WindowManager
import android.widget.ImageView
// LinearLayout is still needed for LayoutParams type
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat

private const val PREFS_NAME = "app_switcher_prefs"
private const val KEY_SELECTED_APPS = "selected_app_packages"
private const val KEY_FLOATING_X = "floating_x"
private const val KEY_FLOATING_Y = "floating_y"

class FloatingActionService : Service() {

    private val CHANNEL_ID = "FloatingActionServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "FloatingActionService"

    private lateinit var windowManager: WindowManager
    private var floatingView: DraggableLinearLayout? = null // Changed to DraggableLinearLayout
    private lateinit var params: WindowManager.LayoutParams

    // Removed initialX, initialY, initialTouchX, initialTouchY as drag logic is in DraggableLinearLayout

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called")
        return null
    }

    // Removed @SuppressLint("ClickableViewAccessibility") as touch is handled by DraggableLinearLayout
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
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // Crucial for allowing touches to pass through for system gestures if not handled
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_FLOATING_X, 100) // Load saved position
            y = prefs.getInt(KEY_FLOATING_Y, 100) // Load saved position
        }

        floatingView = DraggableLinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            // Pass WindowManager, params, and SharedPreferences details to the custom view
            setWindowManagerParams(windowManager, params, prefs, KEY_FLOATING_X, KEY_FLOATING_Y)
        }

        // The setOnTouchListener is removed as DraggableLinearLayout handles its own touch events.

        try {
            if (floatingView?.windowToken == null) {
                 Log.d(TAG, "Adding floating view to WindowManager with params: x=${params.x}, y=${params.y}")
                windowManager.addView(floatingView, params)
            } else {
                // If view already has a token, it might have been added by a previous onCreate
                // or the service was restarted. Update its layout if position changed.
                Log.d(TAG, "Floating view already has window token, updating layout. Params: x=${params.x}, y=${params.y}")
                windowManager.updateViewLayout(floatingView, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding/updating view in WindowManager: ${e.message}", e)
            floatingView = null // Nullify to prevent further issues
        }
        refreshAppIconsView()
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
            // Still refresh, in case the view is out of sync for some other reason
            refreshAppIconsView()
        }
    }

    private fun refreshAppIconsView() {
        val currentFloatingView = floatingView ?: run {
            Log.e(TAG, "FloatingView is null, cannot refresh icons.")
            return
        }
        currentFloatingView.removeAllViews() // DraggableLinearLayout is a ViewGroup

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val selectedAppPackagesSet = prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        Log.d(TAG, "Refreshing icons. Selected apps: $selectedAppPackagesSet")

        if (selectedAppPackagesSet.isEmpty()) {
            Log.d(TAG, "No apps selected to display in floating view.")
            // Optionally, make the view GONE or INVISIBLE if it's empty
            // currentFloatingView.visibility = View.GONE
            return
        }
        // else {
        //    currentFloatingView.visibility = View.VISIBLE
        // }

        val localPackageManager = applicationContext.packageManager
        val iconSize = (48 * resources.displayMetrics.density).toInt()
        val selectedAppPackagesList = selectedAppPackagesSet.toList() // Convert Set to List

        selectedAppPackagesList.forEachIndexed { index, packageName -> // Use forEachIndexed
            try {
                val appInfo = localPackageManager.getApplicationInfo(packageName, 0)
                val appLabel = localPackageManager.getApplicationLabel(appInfo).toString()
                val appIcon: Drawable = localPackageManager.getApplicationIcon(packageName)

                val imageView = ImageView(this).apply {
                    setImageDrawable(appIcon)
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        // Only add bottomMargin if it's NOT the last item
                        if (index < selectedAppPackagesList.lastIndex) {
                            bottomMargin = 8.dpToPx()
                        }
                    }
                    contentDescription = appLabel
                    isClickable = true // Explicitly set, helps with touch event handling
                    isFocusable = true  // Can also help with accessibility and distinguishing touch targets

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
                removeAppFromSwitcher(packageName, packageName) // Use packageName as fallback label
            } catch (e: Exception) {
                Log.e(TAG, "Error loading icon/info for $packageName during icon refresh: ${e.message}", e)
                removeAppFromSwitcher(packageName, packageName) // Use packageName as fallback label
            }
        }
    }

    fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called. Intent: $intent, Flags: $flags, StartId: $startId")

        // Ensure the view is created and added if the service is restarted
        // and floatingView is null or not attached.
        if (floatingView == null) {
            Log.w(TAG, "onStartCommand: floatingView is null, attempting to recreate via onCreate().")
            // Calling onCreate() directly is unconventional.
            // Better to have a separate initView() method if this is a common recovery path.
            // However, if the service is killed and restarted by the system, onCreate will be called.
            // This explicit call might be for cases where the service is restarted via startService
            // after it has been created but its view was somehow lost or not added.
            // Let's rely on onCreate being called or having already been called.
            // If it's already created but not attached, that's handled in onCreate's addView logic.
        } else if (floatingView?.windowToken == null) {
             Log.w(TAG, "onStartCommand: floatingView exists but not attached to window. Attempting to re-add.")
             // This situation might occur if the service was stopped (onDestroy removed view)
             // and then restarted, and onCreate was *not* called again by the system (e.g. START_STICKY killed process).
             // However, our current onCreate loads position and tries to addView/updateViewLayout.
             // Let's ensure params reflect latest saved position from prefs for re-adding.
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            params.x = prefs.getInt(KEY_FLOATING_X, params.x) // Use current if not found, or loaded from onCreate
            params.y = prefs.getInt(KEY_FLOATING_Y, params.y)
            try {
                if (floatingView?.parent == null) { // Check if it's not already in some hierarchy (though windowToken is better)
                    Log.d(TAG, "Re-adding floating view in onStartCommand. Pos: x=${params.x}, y=${params.y}")
                    windowManager.addView(floatingView, params)
                } else {
                    Log.d(TAG, "Floating view has parent, updating layout in onStartCommand. Pos: x=${params.x}, y=${params.y}")
                    windowManager.updateViewLayout(floatingView,params)
                }
                refreshAppIconsView() // Refresh icons as well
            } catch (e: IllegalStateException) {
                 Log.e(TAG, "View already added or other issue in onStartCommand re-add: ${e.message}")
                 // If already added, try to update.
                 try { windowManager.updateViewLayout(floatingView, params) } catch (updE: Exception) {
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
            .setSmallIcon(R.mipmap.ic_launcher) // Ensure this icon exists
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Use LOW or MIN for less intrusive ongoing notifications
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "startForeground called successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error calling startForeground: ${e.message}", e)
            // Consider stopping the service if foregrounding fails critically for an overlay app
            // stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
        // Position is saved by DraggableLinearLayout on ACTION_UP.
        // No explicit save needed here unless as a last resort fallback.
        floatingView?.let {
            try {
                if (it.windowToken != null) { // Check if view is still attached
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
            val importance = NotificationManager.IMPORTANCE_LOW // Use LOW for ongoing task, not urgent
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

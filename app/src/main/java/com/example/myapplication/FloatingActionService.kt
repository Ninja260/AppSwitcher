package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.graphics.toColorInt

class FloatingActionService : Service() {

    companion object {
        const val ACTION_REFRESH_FLOATING_VIEW =
            "com.example.myapplication.ACTION_REFRESH_FLOATING_VIEW"
        const val ACTION_SUPPRESS_UI = "com.example.myapplication.ACTION_SUPPRESS_UI"
        const val ACTION_UNSUPPRESS_UI = "com.example.myapplication.ACTION_UNSUPPRESS_UI"
        const val PREFS_NAME = "app_switcher_prefs"
        const val KEY_SELECTED_APPS = "selected_app_packages"
        const val KEY_FLOATING_X = "floating_x"
        const val KEY_FLOATING_Y = "floating_y"
        const val KEY_FLOATING_ALPHA = "floating_alpha"
        const val KEY_FLOATING_ICON_SIZE = "floating_icon_size"
        const val KEY_MAX_DOCK_APPS = "max_dock_apps" // New key for max dockable apps
        @Volatile
        var isServiceRunning: Boolean = false
    }

    private val channelId = "FloatingActionServiceChannel"
    private val notificationId = 1
    private val tagName = "FloatingActionService"

    private lateinit var windowManager: WindowManager
    private var floatingView: DraggableLinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private var isUiSuppressed: Boolean = false

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(tagName, "onBind called")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tagName, "onCreate called")
        createNotificationChannel()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

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
            x = prefs.getInt(KEY_FLOATING_X, 0)
            y = prefs.getInt(KEY_FLOATING_Y, 100)
        }

        floatingView = DraggableLinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            
            // Make corner radius proportional to icon size
            val iconSizeInDp = prefs.getInt(KEY_FLOATING_ICON_SIZE, 48)
            val cornerRadiusInDp = iconSizeInDp / 3 // Proportion: 1/3 of icon size
            val cornerRadiusInPx = cornerRadiusInDp.dpToPx().toFloat()

            val backgroundDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor("#80000000".toColorInt()) // Set background color
                setCornerRadius(cornerRadiusInPx)      // Set proportional corner radius
            }
            background = backgroundDrawable         // Apply the drawable as background
            val initialPadding = (cornerRadiusInDp / 2).coerceAtLeast(8).dpToPx() // Adjust padding based on radius, with a minimum
            setPadding(initialPadding, initialPadding, initialPadding, initialPadding)
            setWindowManagerParams(windowManager, params, prefs, KEY_FLOATING_X, KEY_FLOATING_Y)
        }

        try {
            if (floatingView?.windowToken == null) {
                Log.d(tagName, "Adding floating view to WindowManager with params: x=${params.x}, y=${params.y}")
                windowManager.addView(floatingView, params)
            } else {
                Log.d(tagName, "Floating view already has window token, updating layout. Params: x=${params.x}, y=${params.y}")
                windowManager.updateViewLayout(floatingView, params)
            }
            applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))
        } catch (e: Exception) {
            Log.e(tagName, "Error adding/updating view in WindowManager: ${e.message}", e)
            floatingView = null 
        }
    }

    private fun applyAlphaToFloatingView(alpha: Float) {
        floatingView?.alpha = alpha
    }

    private fun removeAppFromSwitcher(packageName: String, appLabel: String) {
        Log.i(tagName, "Removing $appLabel ($packageName) from switcher due to launch failure.")
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        val currentSelectedApps = prefs.getStringSet(KEY_SELECTED_APPS, HashSet()) ?: HashSet()
        val newSelectedApps = HashSet(currentSelectedApps)

        if (newSelectedApps.remove(packageName)) {
            editor.putStringSet(KEY_SELECTED_APPS, newSelectedApps).apply()
            Log.i(tagName, "$packageName removed from SharedPreferences.")
            Toast.makeText(applicationContext, "$appLabel removed from switcher.", Toast.LENGTH_LONG).show()
        } else {
            Log.w(tagName, "$packageName was already removed or not found in SharedPreferences.")
        }
        if(isServiceRunning) refreshAppIconsView()
    }

    private fun refreshAppIconsView() {
        val currentFloatingView = floatingView ?: run {
            Log.e(tagName, "FloatingView is null, cannot refresh icons.")
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))

        if (isUiSuppressed) {
            Log.d(tagName, "UI is suppressed, setting view to GONE.")
            currentFloatingView.visibility = View.GONE
            return
        }

        currentFloatingView.removeAllViews()

        val selectedAppPackagesSet = prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        val maxDockApps = prefs.getInt(KEY_MAX_DOCK_APPS, 4)
        Log.d(tagName, "Refresh: Selected apps: $selectedAppPackagesSet (Max: $maxDockApps)")

        if (selectedAppPackagesSet.isEmpty()) {
            Log.d(tagName, "No apps selected, setting view to GONE.")
            // Update background and padding if corner radius might have changed and no apps are shown
            val iconSizeInDpOnEmpty = prefs.getInt(KEY_FLOATING_ICON_SIZE, 48)
            val cornerRadiusInDpOnEmpty = iconSizeInDpOnEmpty / 3
            val cornerRadiusInPxOnEmpty = cornerRadiusInDpOnEmpty.dpToPx().toFloat()
            val backgroundDrawableOnEmpty = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor("#00000000".toColorInt()) // Transparent when empty if desired, or keep semi-transparent
                setCornerRadius(cornerRadiusInPxOnEmpty)
            }
            currentFloatingView.background = backgroundDrawableOnEmpty
            currentFloatingView.setPadding(0, 0, 0, 0) // No padding when empty
            currentFloatingView.visibility = View.GONE
            return
        } else {
            currentFloatingView.visibility = View.VISIBLE
            Log.d(tagName, "Apps selected, ensuring default padding and visibility for floating view.")
            // Update background and padding if corner radius might have changed
            val iconSizeInDp = prefs.getInt(KEY_FLOATING_ICON_SIZE, 48)
            val cornerRadiusInDp = iconSizeInDp / 3
            val cornerRadiusInPx = cornerRadiusInDp.dpToPx().toFloat()
            val backgroundDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor("#80000000".toColorInt())
                setCornerRadius(cornerRadiusInPx)
            }
            currentFloatingView.background = backgroundDrawable
            val paddingInPx = (cornerRadiusInDp / 2).coerceAtLeast(8).dpToPx()
            currentFloatingView.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)
        }

        val localPackageManager = applicationContext.packageManager
        val iconSizeInDp = prefs.getInt(KEY_FLOATING_ICON_SIZE, 48)
        val iconSizeInPx = iconSizeInDp.dpToPx()

        val appsToDisplayList = selectedAppPackagesSet.toList().take(maxDockApps)

        appsToDisplayList.forEachIndexed { index, packageName ->
            try {
                val appInfo = localPackageManager.getApplicationInfo(packageName, 0)
                val appLabel = localPackageManager.getApplicationLabel(appInfo).toString()
                val appIcon: Drawable = localPackageManager.getApplicationIcon(packageName)

                val imageView = ImageView(this).apply {
                    setImageDrawable(appIcon)
                    layoutParams = LinearLayout.LayoutParams(iconSizeInPx, iconSizeInPx).apply {
                        if (index < appsToDisplayList.lastIndex) { 
                            bottomMargin = 8.dpToPx() // This padding might also need to be dynamic if icons are very close to edge
                        }
                    }
                    contentDescription = appLabel
                    isClickable = true
                    isFocusable = true

                    setOnClickListener {
                        Log.d(tagName, "Icon tapped for $packageName ($appLabel)")
                        try {
                            val launchIntent = localPackageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(launchIntent)
                                Log.i(tagName, "Launched $packageName successfully.")
                            } else {
                                Log.e(tagName, "Could not get launch intent for $packageName.")
                                removeAppFromSwitcher(packageName, appLabel)
                            }
                        } catch (e: ActivityNotFoundException) {
                            Log.e(tagName, "Activity not found for $packageName.", e)
                            removeAppFromSwitcher(packageName, appLabel)
                        } catch (e: Exception) {
                            Log.e(tagName, "Error launching $packageName: ${e.message}", e)
                            removeAppFromSwitcher(packageName, appLabel)
                        }
                    }
                }
                currentFloatingView.addView(imageView)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(tagName, "App not found during icon refresh: $packageName. Removing.", e)
                removeAppFromSwitcher(packageName, packageName) 
            } catch (e: Exception) {
                Log.e(tagName, "Error loading icon/info for $packageName during icon refresh: ${e.message}", e)
            }
        }
    }

    fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tagName, "onStartCommand called. Intent Action: ${intent?.action}, Flags: $flags, StartId: $startId")
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        when (intent?.action) {
            ACTION_SUPPRESS_UI -> {
                Log.d(tagName, "Received ACTION_SUPPRESS_UI")
                isUiSuppressed = true
                if (isServiceRunning) {
                    floatingView?.visibility = View.GONE
                }
            }
            ACTION_UNSUPPRESS_UI -> {
                Log.d(tagName, "Received ACTION_UNSUPPRESS_UI")
                isUiSuppressed = false
                if (isServiceRunning) {
                    applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))
                    refreshAppIconsView() // This will now also update corner radius
                }
            }
            ACTION_REFRESH_FLOATING_VIEW -> {
                Log.d(tagName, "Received ACTION_REFRESH_FLOATING_VIEW.")
                if (isServiceRunning) {
                    if (floatingView != null && floatingView?.windowToken != null) {
                        Log.d(tagName, "Calling refreshAppIconsView for ACTION_REFRESH_FLOATING_VIEW.")
                        applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))
                        refreshAppIconsView() // This will now also update corner radius
                    } else {
                        Log.w(tagName, "Refresh action received but view is not ready.")
                    }
                } else {
                    Log.d(tagName, "ACTION_REFRESH_FLOATING_VIEW received, but service is not running. Ignoring UI refresh.")
                }
            }
            null -> { // Explicit service start command
                Log.d(tagName, "Null action: Explicit service start command.")
                isUiSuppressed = false

                if (floatingView == null) {
                     // onCreate should have initialized it. If it's null here, something is wrong.
                    Log.e(tagName, "Start command: floatingView is unexpectedly null. Service cannot function. Stopping.")
                     stopSelf()
                     return START_NOT_STICKY
                } else if (floatingView?.windowToken == null) {
                    Log.w(tagName, "Start command: floatingView exists but not attached. Attempting to re-add.")
                    params.x = prefs.getInt(KEY_FLOATING_X, params.x)
                    params.y = prefs.getInt(KEY_FLOATING_Y, params.y)
                    try {
                        // Ensure corner radius is up-to-date before re-adding
                        val iconSizeInDp = prefs.getInt(KEY_FLOATING_ICON_SIZE, 48)
                        val cornerRadiusInDp = iconSizeInDp / 3
                        val cornerRadiusInPx = cornerRadiusInDp.dpToPx().toFloat()
                        val backgroundDrawable = (floatingView?.background?.mutate() as? GradientDrawable) ?: GradientDrawable()
                        backgroundDrawable.shape = GradientDrawable.RECTANGLE
                        backgroundDrawable.setColor("#80000000".toColorInt())
                        backgroundDrawable.cornerRadius = cornerRadiusInPx
                        floatingView?.background = backgroundDrawable
                        val paddingInPx = (cornerRadiusInDp / 2).coerceAtLeast(8).dpToPx()
                        floatingView?.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)

                        if (floatingView?.parent == null) {
                            windowManager.addView(floatingView, params)
                        } else {
                            windowManager.updateViewLayout(floatingView, params)
                        }
                         applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))
                    } catch (e: Exception) {
                        Log.e(tagName, "Error re-adding/updating view in Start command: ${e.message}", e)
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
                
                applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))
                refreshAppIconsView() // This will now also update corner radius

                val notificationIntent = Intent(this, SettingsActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(this, channelId)
                    .setContentTitle("App Switcher Active")
                    .setContentText("Floating action is running.")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                try {
                    startForeground(notificationId, notification)
                    isServiceRunning = true
                    Log.d(tagName, "startForeground called successfully. isServiceRunning: $isServiceRunning")
                } catch (e: Exception) {
                    isServiceRunning = false
                    Log.e(tagName, "Error calling startForeground: ${e.message}. isServiceRunning: $isServiceRunning", e)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            else -> {
                Log.w(tagName, "onStartCommand: Received unknown action: ${intent.action}. Ignoring.")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(tagName, "onDestroy called. isServiceRunning was: $isServiceRunning")
        isServiceRunning = false
        super.onDestroy()
        floatingView?.let {
            try {
                if (it.windowToken != null) {
                    Log.d(tagName, "Removing floating view from WindowManager")
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                Log.e(tagName, "Error removing view from WindowManager in onDestroy: ${e.message}", e)
            }
            floatingView = null
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(channelId)
        }
        Log.d(tagName, "onDestroy: Service fully stopped and cleaned up.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Floating Action Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            Log.d(tagName, "Notification channel created.")
        } else {
            Log.d(tagName, "Notification channel not needed for this API level.")
        }
    }
}

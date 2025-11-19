package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
        const val KEY_MAX_DOCK_APPS = "max_dock_apps"
        const val KEY_FLOATING_MINIMIZED_STATE = "floating_minimized_state" // New key
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
            // The DraggableLinearLayout now handles its own orientation and children
            val iconSizeInDp = prefs.getInt(KEY_FLOATING_ICON_SIZE, 48)
            val cornerRadiusInDp = iconSizeInDp / 3 
            val cornerRadiusInPx = cornerRadiusInDp.dpToPx().toFloat()

            val backgroundDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor("#80000000".toColorInt()) 
                setCornerRadius(cornerRadiusInPx) 
            }
            background = backgroundDrawable         
            val initialPadding = (cornerRadiusInDp / 2).coerceAtLeast(4).dpToPx() // Reduced min padding slightly
            setPadding(initialPadding, initialPadding, initialPadding, initialPadding)
            
            // Pass all necessary keys to DraggableLinearLayout
            setWindowManagerParams(
                windowManager, 
                params, 
                prefs, 
                KEY_FLOATING_X, 
                KEY_FLOATING_Y,
                KEY_FLOATING_MINIMIZED_STATE // Pass the new key
            )
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

        // Overall visibility of the floatingView
        if (isUiSuppressed) {
            Log.d(tagName, "UI is suppressed, setting entire floating view to GONE.")
            currentFloatingView.visibility = View.GONE
            return
        }

        val selectedAppPackagesSet = prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        if (selectedAppPackagesSet.isEmpty() && !currentFloatingView.isCurrentlyMinimized()) {
            Log.d(tagName, "No apps selected AND not minimized, setting entire floating view GONE.")
            // Keep background styling minimal if it's going GONE anyway
            val backgroundDrawableOnEmpty = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor("#00000000".toColorInt()) 
                setCornerRadius(0f) // No radius if it's GONE
            }
            currentFloatingView.background = backgroundDrawableOnEmpty
            currentFloatingView.setPadding(0, 0, 0, 0)
            currentFloatingView.visibility = View.GONE
            return
        } else {
            // If UI is not suppressed, and (we have apps OR it's minimized), make it visible.
            currentFloatingView.visibility = View.VISIBLE
            Log.d(tagName, "Apps selected or view is minimized, ensuring floating view is VISIBLE.")
            // Update background and padding (proportional to icon size)
            val iconSizeInDp = prefs.getInt(KEY_FLOATING_ICON_SIZE, 48)
            val cornerRadiusInDp = iconSizeInDp / 3
            val cornerRadiusInPx = cornerRadiusInDp.dpToPx().toFloat()
            val backgroundDrawable = (currentFloatingView.background?.mutate() as? GradientDrawable) ?: GradientDrawable()
            backgroundDrawable.shape = GradientDrawable.RECTANGLE
            // Color depends on whether it's minimized and has apps or not
            if (currentFloatingView.isCurrentlyMinimized() || selectedAppPackagesSet.isNotEmpty()) {
                backgroundDrawable.setColor("#80000000".toColorInt())
            } else {
                 backgroundDrawable.setColor("#00000000".toColorInt()) // Transparent if empty AND not minimized (edge case)
            }
            backgroundDrawable.cornerRadius = cornerRadiusInPx
            currentFloatingView.background = backgroundDrawable
            val paddingInPx = (cornerRadiusInDp / 2).coerceAtLeast(4).dpToPx()
            currentFloatingView.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)
        }

        // Now manage the appIconsContainer within the DraggableLinearLayout
        currentFloatingView.appIconsContainer.removeAllViews()

        if (currentFloatingView.isCurrentlyMinimized()) {
            Log.d(tagName, "Floating view is minimized, appIconsContainer will be GONE (handled by DraggableLinearLayout).")
            // DraggableLinearLayout itself handles making appIconsContainer GONE
        } else if (selectedAppPackagesSet.isEmpty()) {
            Log.d(tagName, "No apps selected and not minimized, appIconsContainer will be empty.")
            // appIconsContainer is already empty and will remain visible but without children
        } else {
            Log.d(tagName, "Apps selected and not minimized, populating appIconsContainer.")
            val localPackageManager = applicationContext.packageManager
            val iconSizeInDp = prefs.getInt(KEY_FLOATING_ICON_SIZE, 48)
            val iconSizeInPx = iconSizeInDp.dpToPx()
            val maxDockApps = prefs.getInt(KEY_MAX_DOCK_APPS, 4)
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
                                bottomMargin = 8.dpToPx()
                            }
                        }
                        contentDescription = appLabel
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { }
                    }
                    currentFloatingView.appIconsContainer.addView(imageView)
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e(tagName, "App not found: $packageName. Removing.", e)
                    removeAppFromSwitcher(packageName, packageName)
                } catch (e: Exception) {
                    Log.e(tagName, "Error loading icon for $packageName: ${e.message}", e)
                }
            }
        }
        if (isServiceRunning) {
            currentFloatingView.requestLayout()
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
                floatingView?.visibility = View.GONE
            }
            ACTION_UNSUPPRESS_UI -> {
                Log.d(tagName, "Received ACTION_UNSUPPRESS_UI")
                isUiSuppressed = false
                // Visibility and content handled by refreshAppIconsView
                if (isServiceRunning) refreshAppIconsView()
            }
            ACTION_REFRESH_FLOATING_VIEW -> {
                Log.d(tagName, "Received ACTION_REFRESH_FLOATING_VIEW.")
                if (isServiceRunning && floatingView?.windowToken != null) {
                    refreshAppIconsView()
                } else {
                    Log.w(tagName, "Refresh action received but view not ready or service not running.")
                }
            }
            null -> { // Explicit service start command
                Log.d(tagName, "Null action: Explicit service start command.")
                isUiSuppressed = false

                if (floatingView == null) {
                    Log.e(tagName, "Start command: floatingView is null. Service cannot function. Stopping.")
                    stopSelf()
                    return START_NOT_STICKY
                } else if (floatingView?.windowToken == null) {
                    Log.w(tagName, "Start command: floatingView exists but not attached. Attempting to re-add.")
                    params.x = prefs.getInt(KEY_FLOATING_X, params.x)
                    params.y = prefs.getInt(KEY_FLOATING_Y, params.y)
                    try {
                        // Ensure DraggableLinearLayout reloads its minimized state correctly
                        floatingView?.setWindowManagerParams(
                            windowManager, 
                            params, 
                            prefs, 
                            KEY_FLOATING_X, 
                            KEY_FLOATING_Y, 
                            KEY_FLOATING_MINIMIZED_STATE
                        )
                        // Update background and padding if needed before re-adding,
                        val iconSizeInDp = prefs.getInt(KEY_FLOATING_ICON_SIZE, 48)
                        val cornerRadiusInDp = iconSizeInDp / 3
                        val cornerRadiusInPx = cornerRadiusInDp.dpToPx().toFloat()
                        val backgroundDrawable = (floatingView?.background?.mutate() as? GradientDrawable) ?: GradientDrawable()
                        backgroundDrawable.shape = GradientDrawable.RECTANGLE
                        backgroundDrawable.setColor("#80000000".toColorInt())
                        backgroundDrawable.cornerRadius = cornerRadiusInPx
                        floatingView?.background = backgroundDrawable
                        val paddingInPx = (cornerRadiusInDp / 2).coerceAtLeast(4).dpToPx()
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
                
                refreshAppIconsView()

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
                } catch (e: Exception) {
                    isServiceRunning = false
                    Log.e(tagName, "Error calling startForeground: ${e.message}", e)
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }
}

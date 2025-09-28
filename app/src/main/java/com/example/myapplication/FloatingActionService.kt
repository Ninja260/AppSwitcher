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
import android.view.View // Required for View.GONE and View.VISIBLE
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
        const val KEY_FLOATING_ALPHA = "floating_alpha" // New key for transparency
        @Volatile
        var isServiceRunning: Boolean = false
    }

    private val CHANNEL_ID = "FloatingActionServiceChannel"
    private val NOTIFICATION_ID = 1
    private val TAG = "FloatingActionService"

    private lateinit var windowManager: WindowManager
    private var floatingView: DraggableLinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private var isUiSuppressed: Boolean = false

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
            x = prefs.getInt(KEY_FLOATING_X, 0)
            y = prefs.getInt(KEY_FLOATING_Y, 100)
            // Alpha will be applied in refreshAppIconsView or when view is created
        }

        floatingView = DraggableLinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Initial background color with full alpha, actual alpha will be applied dynamically
            setBackgroundColor("#80000000".toColorInt()) 
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
            // Apply initial alpha after view is added
            applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))
        } catch (e: Exception) {
            Log.e(TAG, "Error adding/updating view in WindowManager: ${e.message}", e)
            floatingView = null // Nullify if adding failed
        }
    }

    private fun applyAlphaToFloatingView(alpha: Float) {
        floatingView?.alpha = alpha
        // Optionally, if you want the background to also have its alpha component scaled:
        // floatingView?.background?.alpha = (255 * alpha).toInt()
        // For DraggableLinearLayout, the alpha on the view itself should be sufficient.
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
        } else {
            Log.w(TAG, "$packageName was already removed or not found in SharedPreferences.")
        }
        if(isServiceRunning) refreshAppIconsView() // Refresh only if service is running
    }

    private fun refreshAppIconsView() {
        val currentFloatingView = floatingView ?: run {
            Log.e(TAG, "FloatingView is null, cannot refresh icons.")
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))

        if (isUiSuppressed) {
            Log.d(TAG, "UI is suppressed, setting view to GONE.")
            currentFloatingView.visibility = View.GONE
            return
        }

        currentFloatingView.removeAllViews()

        val selectedAppPackagesSet = prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        Log.d(TAG, "Refresh: Reading from SharedPreferences. Selected apps: $selectedAppPackagesSet. Is empty: ${selectedAppPackagesSet.isEmpty()}")

        if (selectedAppPackagesSet.isEmpty()) {
            Log.d(TAG, "No apps selected, setting view to GONE.")
            currentFloatingView.setPadding(0, 0, 0, 0)
            currentFloatingView.visibility = View.GONE
            return
        } else {
            currentFloatingView.visibility = View.VISIBLE
            Log.d(TAG, "Apps selected, ensuring default padding and visibility for floating view.")
            val paddingInPx = 8.dpToPx()
            currentFloatingView.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)
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
                    // Alpha for individual icons is managed by the parent floatingView's alpha

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
                removeAppFromSwitcher(packageName, packageName) 
            } catch (e: Exception) {
                Log.e(TAG, "Error loading icon/info for $packageName during icon refresh: ${e.message}", e)
            }
        }
    }

    fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called. Intent Action: ${intent?.action}, Flags: $flags, StartId: $startId")
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        when (intent?.action) {
            ACTION_SUPPRESS_UI -> {
                Log.d(TAG, "Received ACTION_SUPPRESS_UI")
                isUiSuppressed = true
                if (isServiceRunning) {
                    floatingView?.visibility = View.GONE
                }
            }
            ACTION_UNSUPPRESS_UI -> {
                Log.d(TAG, "Received ACTION_UNSUPPRESS_UI")
                isUiSuppressed = false
                if (isServiceRunning) {
                    applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))
                    refreshAppIconsView()
                }
            }
            ACTION_REFRESH_FLOATING_VIEW -> {
                Log.d(TAG, "Received ACTION_REFRESH_FLOATING_VIEW.")
                if (isServiceRunning) {
                    if (floatingView != null && floatingView?.windowToken != null) {
                        Log.d(TAG, "Calling refreshAppIconsView for ACTION_REFRESH_FLOATING_VIEW.")
                        applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))
                        refreshAppIconsView()
                    } else {
                        Log.w(TAG, "Refresh action received but view is not ready.")
                    }
                } else {
                    Log.d(TAG, "ACTION_REFRESH_FLOATING_VIEW received, but service is not running. Ignoring UI refresh.")
                }
            }
            null -> { // Explicit service start command
                Log.d(TAG, "Null action: Explicit service start command.")
                isUiSuppressed = false // Ensure UI is not suppressed on explicit start

                if (floatingView == null) {
                     Log.e(TAG, "Start command: floatingView is null. Service cannot function. Stopping.")
                     stopSelf()
                     return START_NOT_STICKY
                } else if (floatingView?.windowToken == null) {
                    Log.w(TAG, "Start command: floatingView exists but not attached. Attempting to re-add.")
                    params.x = prefs.getInt(KEY_FLOATING_X, params.x)
                    params.y = prefs.getInt(KEY_FLOATING_Y, params.y)
                    try {
                        if (floatingView?.parent == null) {
                            windowManager.addView(floatingView, params)
                        } else {
                            windowManager.updateViewLayout(floatingView, params)
                        }
                         applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error re-adding/updating view in Start command: ${e.message}", e)
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
                
                applyAlphaToFloatingView(prefs.getFloat(KEY_FLOATING_ALPHA, 1.0f))
                refreshAppIconsView() 

                val notificationIntent = Intent(this, SettingsActivity::class.java)
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
                    isServiceRunning = true
                    Log.d(TAG, "startForeground called successfully. isServiceRunning: $isServiceRunning")
                } catch (e: Exception) {
                    isServiceRunning = false
                    Log.e(TAG, "Error calling startForeground: ${e.message}. isServiceRunning: $isServiceRunning", e)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            else -> {
                Log.w(TAG, "onStartCommand: Received unknown action: ${intent.action}. Ignoring.")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called. isServiceRunning was: $isServiceRunning")
        isServiceRunning = false
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
        Log.d(TAG, "onDestroy finished. isServiceRunning: $isServiceRunning")
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

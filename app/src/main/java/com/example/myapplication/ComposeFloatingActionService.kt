package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

class ComposeFloatingActionService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

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
        const val KEY_FLOATING_MINIMIZED_STATE = "floating_minimized_state"
        @Volatile
        var isServiceRunning: Boolean = false
    }

    private val channelId = "ComposeFloatingActionServiceChannel"
    private val notificationId = 2 // Use a different ID from the old service

    // Lifecycle and state properties
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private val tagName = "ComposeFloatingActionService"
    private lateinit var viewModel: FloatingActionViewModel

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        isServiceRunning = true
        Log.d(tagName, "onCreate called. isServiceRunning = true")

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val viewModelFactory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return FloatingActionViewModel(prefs) as T
            }
        }
        viewModel = ViewModelProvider(this, viewModelFactory)[FloatingActionViewModel::class.java]

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ComposeFloatingActionService)
            setViewTreeViewModelStoreOwner(this@ComposeFloatingActionService)
            setViewTreeSavedStateRegistryOwner(this@ComposeFloatingActionService)

            setContent {
                FloatingActionComposable(viewModel)
            }
        }

        windowManager.addView(composeView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tagName, "onStartCommand called. Intent Action: ${intent?.action}")

        val notificationIntent = Intent(this, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("App Switcher is Active")
            .setContentText("Floating action is running.")
            .setSmallIcon(R.mipmap.ic_launcher) // Use a standard mipmap icon
            .setContentIntent(pendingIntent)
            .build()

        startForeground(notificationId, notification)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        Log.d(tagName, "onDestroy called. isServiceRunning = false")
        windowManager.removeView(composeView)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "App Switcher Service"
            val descriptionText = "Channel for App Switcher foreground service"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

@Composable
fun FloatingActionComposable(viewModel: FloatingActionViewModel) {
    val uiState = viewModel.uiState.value

    Box(
        modifier = Modifier
            .offset {
                IntOffset(uiState.x, uiState.y)
            }
            .pointerInput(Unit) {
                detectDragGestures {
                    change, dragAmount ->
                    change.consume()
                    val currentX = viewModel.uiState.value.x
                    val currentY = viewModel.uiState.value.y
                    viewModel.updatePosition(
                        currentX + dragAmount.x.roundToInt(),
                        currentY + dragAmount.y.roundToInt()
                    )
                }
            }
    ) {
        Text("Hello, Compose! X=${uiState.x}, Y=${uiState.y}")
    }
}

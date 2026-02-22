package com.example.myapplication

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

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

    // Lifecycle and state properties
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private val tagName = "ComposeFloatingActionService"

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        isServiceRunning = true
        Log.d(tagName, "onCreate called. isServiceRunning = true")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        composeView = ComposeView(this).apply {
            // Attach the lifecycle and state owners to the view using modern extension functions
            setViewTreeLifecycleOwner(this@ComposeFloatingActionService)
            setViewTreeViewModelStoreOwner(this@ComposeFloatingActionService)
            setViewTreeSavedStateRegistryOwner(this@ComposeFloatingActionService)

            setContent {
                FloatingActionComposable()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(composeView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tagName, "onStartCommand called. Intent Action: ${intent?.action}")
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
}

@Composable
fun FloatingActionComposable() {
    Text("Hello, Compose!")
}

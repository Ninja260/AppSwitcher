package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private sealed class AppDataLoadState {
    object Loading : AppDataLoadState()
    data class Loaded(val bitmap: ImageBitmap, val label: String) : AppDataLoadState()
    object NotFound : AppDataLoadState()
}

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
    private lateinit var params: WindowManager.LayoutParams
    private val tagName = "ComposeFloatingActionService"
    private lateinit var viewModel: FloatingActionViewModel

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun updateWindowPosition(x: Int, y: Int) {
        if (::composeView.isInitialized && composeView.isAttachedToWindow) {
            params.x = x
            params.y = y
            windowManager.updateViewLayout(composeView, params)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        isServiceRunning = true
        Log.d(tagName, "onCreate called. isServiceRunning = true")

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val viewModelFactory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(FloatingActionViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return FloatingActionViewModel(prefs) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
        viewModel = ViewModelProvider(this, viewModelFactory)[FloatingActionViewModel::class.java]

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = viewModel.uiState.value.x
            y = viewModel.uiState.value.y
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ComposeFloatingActionService)
            setViewTreeViewModelStoreOwner(this@ComposeFloatingActionService)
            setViewTreeSavedStateRegistryOwner(this@ComposeFloatingActionService)

            setContent {
                FloatingActionComposable(
                    viewModel = viewModel,
                    onPositionUpdate = ::updateWindowPosition
                )
            }
        }

        windowManager.addView(composeView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tagName, "onStartCommand called. Intent Action: ${intent?.action}")

        val notificationIntent = Intent(this, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
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
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

@Composable
fun FloatingActionComposable(
    viewModel: FloatingActionViewModel,
    onPositionUpdate: (Int, Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    var composableWidth by remember { mutableIntStateOf(0) }

    var offsetX by remember { mutableFloatStateOf(uiState.x.toFloat()) }
    var offsetY by remember { mutableFloatStateOf(uiState.y.toFloat()) }

    LaunchedEffect(uiState.x, uiState.y) {
        offsetX = uiState.x.toFloat()
        offsetY = uiState.y.toFloat()
    }

    Column(
        modifier = Modifier
            .onSizeChanged { composableWidth = it.width }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        offsetX = uiState.x.toFloat()
                        offsetY = uiState.y.toFloat()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        onPositionUpdate(offsetX.roundToInt(), offsetY.roundToInt())
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            val finalY = offsetY.roundToInt()
                            val currentX = offsetX
                            val targetX = if (currentX + composableWidth / 2 < screenWidth / 2) 0f else screenWidth - composableWidth

                            Animatable(currentX).animateTo(
                                targetX,
                                animationSpec = tween(durationMillis = 300)
                            ) { 
                                offsetX = this.value
                                onPositionUpdate(offsetX.roundToInt(), finalY)
                            }
                            // Persist the final position
                            viewModel.updatePosition(targetX.roundToInt(), finalY, saveToPrefs = true)
                        }
                    }
                )
            }
            .graphicsLayer(alpha = uiState.alpha)
            .background(
                color = Color.Black,
                shape = RoundedCornerShape((uiState.iconSize / 3).dp)
            )
            .padding(((uiState.iconSize / 3) / 2).coerceAtLeast(4).dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = { viewModel.toggleMinimized() }) {
            Icon(
                imageVector = if (uiState.isMinimized) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (uiState.isMinimized) "Expand" else "Minimize",
                tint = Color.White
            )
        }
        if (!uiState.isMinimized) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val context = LocalContext.current
                val packageManager = context.packageManager
                val appsToDisplay = uiState.selectedApps.sorted().take(uiState.maxDockApps)

                appsToDisplay.forEach { packageName ->
                    key(packageName) {
                        val loadState by produceState<AppDataLoadState>(initialValue = AppDataLoadState.Loading, packageName, uiState.iconSize) {
                            value = withContext(Dispatchers.IO) {
                                try {
                                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                                    val iconDrawable = packageManager.getApplicationIcon(appInfo)
                                    val appLabel = packageManager.getApplicationLabel(appInfo).toString()
                                    val iconSizePx = with(density) { uiState.iconSize.dp.toPx().toInt() }
                                    val bitmap = iconDrawable.toBitmap(width = iconSizePx, height = iconSizePx).asImageBitmap()
                                    AppDataLoadState.Loaded(bitmap, appLabel)
                                } catch (_: PackageManager.NameNotFoundException) {
                                    AppDataLoadState.NotFound
                                }
                            }
                        }

                        when (val state = loadState) {
                            is AppDataLoadState.Loading -> {
                                Box(modifier = Modifier.size(uiState.iconSize.dp))
                            }
                            is AppDataLoadState.Loaded -> {
                                Image(
                                    bitmap = state.bitmap,
                                    contentDescription = state.label,
                                    modifier = Modifier
                                        .size(uiState.iconSize.dp)
                                        .clickable {
                                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                                            if (launchIntent != null) {
                                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(launchIntent)
                                            } else {
                                                viewModel.removeApp(packageName)
                                            }
                                        }
                                )
                            }
                            is AppDataLoadState.NotFound -> {
                                Box(modifier = Modifier.size(uiState.iconSize.dp))
                                LaunchedEffect(packageName) {
                                    viewModel.removeApp(packageName)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.myapplication

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlin.math.roundToInt


class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    SettingsNavigator()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (ComposeFloatingActionService.isServiceRunning) {
            Log.d("SettingsActivity", "onStop: Service is running, sending UNSUPPRESS_UI intent.")
            val intent = Intent(this, ComposeFloatingActionService::class.java).apply {
                action = ComposeFloatingActionService.ACTION_UNSUPPRESS_UI
            }
            startService(intent)
        }
    }
}

@Composable
fun SettingsNavigator() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main_settings") {
        composable("main_settings") {
            MainSettingsScreen(navController = navController)
        }
        composable("app_selection") {
            SettingsScreen(navController = navController)
        }
    }
}

private fun startFloatingActionService(context: Context) {
    val intent = Intent(context, ComposeFloatingActionService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopFloatingActionService(context: Context) {
    val intent = Intent(context, ComposeFloatingActionService::class.java)
    context.stopService(intent)
}

private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
    val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    for (service in enabledServices) {
        if (service.resolveInfo.serviceInfo.name == serviceClass.name) {
            return true
        }
    }
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isServiceEnabled by remember { mutableStateOf(ComposeFloatingActionService.isServiceRunning) }

    val sharedPreferences = remember {
        context.getSharedPreferences(ComposeFloatingActionService.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var selectedAppCount by remember {
        mutableIntStateOf(
            sharedPreferences.getStringSet(
                ComposeFloatingActionService.KEY_SELECTED_APPS, emptySet()
            )?.size ?: 0
        )
    }

    var currentAlpha by remember {
        mutableFloatStateOf(sharedPreferences.getFloat(ComposeFloatingActionService.KEY_FLOATING_ALPHA, 1.0f))
    }

    var currentIconSizeDp by remember {
        mutableFloatStateOf(sharedPreferences.getInt(ComposeFloatingActionService.KEY_FLOATING_ICON_SIZE, 48).toFloat())
    }

    var currentMaxDockApps by remember {
        mutableFloatStateOf(sharedPreferences.getInt(ComposeFloatingActionService.KEY_MAX_DOCK_APPS, 4).toFloat())
    }

    var isHotkeyServiceEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context, HotkeyService::class.java)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = ComposeFloatingActionService.isServiceRunning
                selectedAppCount = sharedPreferences.getStringSet(
                    ComposeFloatingActionService.KEY_SELECTED_APPS, emptySet()
                )?.size ?: 0
                currentAlpha = sharedPreferences.getFloat(ComposeFloatingActionService.KEY_FLOATING_ALPHA, 1.0f)
                currentIconSizeDp = sharedPreferences.getInt(ComposeFloatingActionService.KEY_FLOATING_ICON_SIZE, 48).toFloat()
                currentMaxDockApps = sharedPreferences.getInt(ComposeFloatingActionService.KEY_MAX_DOCK_APPS, 4).toFloat()
                isHotkeyServiceEnabled = isAccessibilityServiceEnabled(context, HotkeyService::class.java)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(sharedPreferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                ComposeFloatingActionService.KEY_SELECTED_APPS -> {
                    selectedAppCount = prefs.getStringSet(key, emptySet())?.size ?: 0
                }
                ComposeFloatingActionService.KEY_FLOATING_ALPHA -> {
                    currentAlpha = prefs.getFloat(key, 1.0f)
                }
                ComposeFloatingActionService.KEY_FLOATING_ICON_SIZE -> {
                    currentIconSizeDp = prefs.getInt(key, 48).toFloat()
                }
                ComposeFloatingActionService.KEY_MAX_DOCK_APPS -> {
                    currentMaxDockApps = prefs.getInt(key, 4).toFloat()
                }
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    lateinit var notificationPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            Log.d("SettingsActivity", "Overlay permission granted.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startServiceLogic(context) { isServiceEnabled = it }
            }
        } else {
            Log.d("SettingsActivity", "Overlay permission NOT granted.")
            isServiceEnabled = false
            Toast.makeText(
                context,
                "Overlay permission is required to display the app switcher.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("SettingsActivity", "Notification permission granted.")
            startServiceLogic(context) { isServiceEnabled = it }
        } else {
            Log.d("SettingsActivity", "Notification permission was denied.")
            isServiceEnabled = false
            Toast.makeText(
                context,
                "Notification permission is required for the App Switcher service.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Switcher Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp), 
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) { 
                    Text(
                        text = "Enable App Switcher",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isServiceEnabled) "App Switcher service running." else "App Switcher service disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(
                            start = 4.dp,
                            top = 8.dp,
                        ) 
                    )
                }
                Switch(
                    checked = isServiceEnabled, onCheckedChange = { isChecked ->
                        sharedPreferences.edit().putBoolean(BootCompletedReceiver.KEY_SERVICE_ENABLED, isChecked).apply()
                        if (isChecked) {
                            if (!Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    "package:${context.packageName}".toUri()
                                )
                                overlayPermissionLauncher.launch(intent)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                startServiceLogic(context) { newState ->
                                    isServiceEnabled = newState
                                }
                            }
                        } else {
                            Log.d("SettingsActivity", "Disabling service via switch.")
                            isServiceEnabled = false
                            stopFloatingActionService(context)
                        }
                    }
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("app_selection") }
                    .padding(vertical = 8.dp), 
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(
                    modifier = Modifier
                        .weight(1f) 
                        .padding(end = 8.dp) 
                ) {
                    Text(
                        text = "Dockable Applications",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (selectedAppCount == 0) "No apps selected." else "$selectedAppCount ${if (selectedAppCount == 1) "app" else "apps"} selected.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(
                            start = 4.dp, top = 8.dp
                        ) 
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Select applications"
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Floating Action Transparency",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(currentAlpha * 100).roundToInt()}%",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Slider(
                    value = currentAlpha,
                    onValueChange = { newValue ->
                        currentAlpha = newValue
                    },
                    onValueChangeFinished = {
                        sharedPreferences.edit {
                            putFloat(ComposeFloatingActionService.KEY_FLOATING_ALPHA, currentAlpha)
                        }
                        if (ComposeFloatingActionService.isServiceRunning) {
                            val intent = Intent(context, ComposeFloatingActionService::class.java).apply {
                                action = ComposeFloatingActionService.ACTION_REFRESH_FLOATING_VIEW
                            }
                            context.startService(intent)
                            Log.d("SettingsActivity", "Alpha changed, sent refresh intent.")
                        }
                    },
                    valueRange = 0.2f..1.0f, 
                    steps = 7, 
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Controls the see-through level of the floating action.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp) 
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Floating Action Icon Size",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${currentIconSizeDp.roundToInt()}dp",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Slider(
                    value = currentIconSizeDp,
                    onValueChange = { newValue ->
                        currentIconSizeDp = newValue
                    },
                    onValueChangeFinished = {
                        sharedPreferences.edit {
                            putInt(ComposeFloatingActionService.KEY_FLOATING_ICON_SIZE, currentIconSizeDp.roundToInt())
                        }
                        if (ComposeFloatingActionService.isServiceRunning) {
                            val intent = Intent(context, ComposeFloatingActionService::class.java).apply {
                                action = ComposeFloatingActionService.ACTION_REFRESH_FLOATING_VIEW
                            }
                            context.startService(intent)
                            Log.d("SettingsActivity", "Icon size changed, sent refresh intent.")
                        }
                    },
                    valueRange = 32f..64f,
                    steps = 15, 
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Controls the size of app icons in the floating action.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Max Dockable Apps",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${currentMaxDockApps.roundToInt()} apps",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Slider(
                    value = currentMaxDockApps,
                    onValueChange = { newValue ->
                        currentMaxDockApps = newValue
                    },
                    onValueChangeFinished = {
                        sharedPreferences.edit {
                            putInt(ComposeFloatingActionService.KEY_MAX_DOCK_APPS, currentMaxDockApps.roundToInt())
                        }
                        if (ComposeFloatingActionService.isServiceRunning) {
                            val intent = Intent(context, ComposeFloatingActionService::class.java).apply {
                                action = ComposeFloatingActionService.ACTION_REFRESH_FLOATING_VIEW
                            }
                            context.startService(intent)
                            Log.d("SettingsActivity", "Max dock apps changed, sent refresh intent.")
                        }
                    },
                    valueRange = 2f..4f, 
                    steps = 1, // Allows 2, 3, 4
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Controls the maximum number of apps shown in the floating action (2-4 apps).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            )
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(
                    text = "Global Hotkey Service",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Enable this service to launch docked apps using keyboard shortcuts from anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = if (isHotkeyServiceEnabled) "Hotkey service is active." else "Hotkey service is disabled.",
                        color = if (isHotkeyServiceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                    Button(onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }) {
                        Text(if (isHotkeyServiceEnabled) "Configure" else "Enable")
                    }
                }
            }
        }
    }
}

private fun startServiceLogic(context: Context, updateSwitchState: (Boolean) -> Unit) {
    val hasOverlay = Settings.canDrawOverlays(context)
    val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    if (hasOverlay && hasNotification) {
        Log.d("SettingsActivity", "All permissions granted. Starting service.")
        startFloatingActionService(context)
        updateSwitchState(true)
    } else {
        Log.w(
            "SettingsActivity",
            "startServiceLogic called but permissions are missing. Overlay: $hasOverlay, Notification: $hasNotification"
        )
        updateSwitchState(false)
        if (!hasOverlay) Toast.makeText(
            context, "Overlay permission is required.", Toast.LENGTH_SHORT
        ).show()
        if (!hasNotification) {
            Toast.makeText(context, "Notification permission is required.", Toast.LENGTH_SHORT)
                .show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (ComposeFloatingActionService.isServiceRunning) {
                    Log.d(
                        "SettingsActivity",
                        "SettingsScreen ON_RESUME, service running, sending SUPPRESS_UI intent."
                    )
                    val intent = Intent(context, ComposeFloatingActionService::class.java).apply {
                        action = ComposeFloatingActionService.ACTION_SUPPRESS_UI
                    }
                    context.startService(intent)
                } else {
                    Log.d(
                        "SettingsActivity",
                        "SettingsScreen ON_RESUME, service NOT running, no SUPPRESS_UI intent sent."
                    )
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            Log.d("SettingsActivity", "SettingsScreen disposed, removing observer.")
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (ComposeFloatingActionService.isServiceRunning) {
                val intent = Intent(context, ComposeFloatingActionService::class.java).apply {
                    action = ComposeFloatingActionService.ACTION_UNSUPPRESS_UI
                }
                context.startService(intent)
                Log.d(
                    "SettingsActivity",
                    "SettingsScreen disposed, service running, sent UNSUPPRESS_UI intent."
                )
            } else {
                Log.d(
                    "SettingsActivity",
                    "SettingsScreen disposed, service not running, no UNSUPPRESS_UI intent sent."
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Applications") }, navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
            )
        }) { paddingValues ->
        SettingsScreenContent(Modifier.padding(paddingValues))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    // State for all installed applications
    var allApps by remember { mutableStateOf(emptyList<AppEntry>()) }
    // State for selected applications (package names)
    val sharedPreferences = remember {
        context.getSharedPreferences(ComposeFloatingActionService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var selectedApps by remember { mutableStateOf(sharedPreferences.getStringSet(ComposeFloatingActionService.KEY_SELECTED_APPS, emptySet()) ?: emptySet()) }
    // State for search query
    var searchQuery by remember { mutableStateOf("") }
    // State for filter toggle
    var showOnlySelected by remember { mutableStateOf(false) }
    // Read the max dock apps limit from SharedPreferences
    val maxDockApps = sharedPreferences.getInt(ComposeFloatingActionService.KEY_MAX_DOCK_APPS, 4)

    val filteredApps = remember(allApps, searchQuery, showOnlySelected, selectedApps) {
        val appsAfterSearch = if (searchQuery.isBlank()) {
            allApps
        } else {
            allApps.filter { it.label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
        }

        if (showOnlySelected) {
            appsAfterSearch.filter { selectedApps.contains(it.packageName) }
        } else {
            appsAfterSearch
        }
    }

    LaunchedEffect(Unit) {
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.enabled } 
            .mapNotNull { appInfo ->
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) {
                        AppEntry(
                            packageName = appInfo.packageName,
                            label = packageManager.getApplicationLabel(appInfo).toString(),
                            icon = packageManager.getApplicationIcon(appInfo)
                        )
                    } else null
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .sortedBy { it.label } 
        allApps = installedApps
    }

    DisposableEffect(sharedPreferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == ComposeFloatingActionService.KEY_SELECTED_APPS) {
                selectedApps = prefs.getStringSet(key, emptySet()) ?: emptySet()
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }


    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Applications") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { /* Handle search action if needed */ })
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showOnlySelected) "Showing Selected Apps" else "Showing All Apps",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 8.dp)
            )
            IconButton(onClick = { showOnlySelected = !showOnlySelected }) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = "Toggle app filter",
                    tint = if (showOnlySelected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        }

        if (filteredApps.isEmpty() && searchQuery.isNotBlank()) {
            Text(
                text = "No applications found matching \"$searchQuery\"",
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        } else if (allApps.isEmpty()) {
            Text(
                text = "Loading applications...",
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppListItem(
                        app = app,
                        isSelected = selectedApps.contains(app.packageName),
                        onSelectionChanged = { packageName, isSelected ->
                            val newSelectedApps = selectedApps.toMutableSet()
                            if (isSelected) {
                                if (newSelectedApps.size >= maxDockApps) {
                                    Toast.makeText(
                                        context,
                                        "Maximum of $maxDockApps apps already selected. Please deselect an app first.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@AppListItem
                                }
                                newSelectedApps.add(packageName)
                            } else {
                                newSelectedApps.remove(packageName)
                            }
                            sharedPreferences.edit {
                                putStringSet(ComposeFloatingActionService.KEY_SELECTED_APPS, newSelectedApps)
                            }
                            if (ComposeFloatingActionService.isServiceRunning) {
                                val refreshIntent = Intent(context, ComposeFloatingActionService::class.java).apply {
                                    action = ComposeFloatingActionService.ACTION_REFRESH_FLOATING_VIEW
                                }
                                context.startService(refreshIntent)
                                Log.d("SettingsActivity", "App selection changed, sent refresh intent.")
                            }
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = DividerDefaults.Thickness,
                        color = DividerDefaults.color
                    )
                }
            }
        }
    }
}


@Composable
fun AppListItem(
    app: AppEntry, isSelected: Boolean, onSelectionChanged: (String, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChanged(app.packageName, !isSelected) } // !isSelected means the desired new state
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = rememberDrawablePainter(drawable = app.icon),
            contentDescription = "${app.label} icon",
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = app.label, modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = isSelected, // current state
            onCheckedChange = { newSelectionState -> onSelectionChanged(app.packageName, newSelectionState) }
        )
    }
}

data class AppEntry(val packageName: String, val label: String, val icon: Drawable)

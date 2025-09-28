package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner // Corrected import
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
        if (FloatingActionService.isServiceRunning) {
            Log.d("SettingsActivity", "onStop: Service is running, sending UNSUPPRESS_UI intent.")
            val intent = Intent(this, FloatingActionService::class.java).apply {
                action = FloatingActionService.ACTION_UNSUPPRESS_UI
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
    val intent = Intent(context, FloatingActionService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopFloatingActionService(context: Context) {
    val intent = Intent(context, FloatingActionService::class.java)
    context.stopService(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isServiceEnabled by remember { mutableStateOf(FloatingActionService.isServiceRunning) }

    val sharedPreferences = remember {
        context.getSharedPreferences(FloatingActionService.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var selectedAppCount by remember {
        mutableIntStateOf(
            sharedPreferences.getStringSet(
                FloatingActionService.KEY_SELECTED_APPS, emptySet()
            )?.size ?: 0
        )
    }

    var currentAlpha by remember {
        mutableFloatStateOf(sharedPreferences.getFloat(FloatingActionService.KEY_FLOATING_ALPHA, 1.0f))
    }

    var currentIconSizeDp by remember {
        mutableFloatStateOf(sharedPreferences.getInt(FloatingActionService.KEY_FLOATING_ICON_SIZE, 48).toFloat())
    }

    var currentMaxDockApps by remember {
        mutableFloatStateOf(sharedPreferences.getInt(FloatingActionService.KEY_MAX_DOCK_APPS, 4).toFloat())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = FloatingActionService.isServiceRunning
                selectedAppCount = sharedPreferences.getStringSet(
                    FloatingActionService.KEY_SELECTED_APPS, emptySet()
                )?.size ?: 0
                currentAlpha = sharedPreferences.getFloat(FloatingActionService.KEY_FLOATING_ALPHA, 1.0f)
                currentIconSizeDp = sharedPreferences.getInt(FloatingActionService.KEY_FLOATING_ICON_SIZE, 48).toFloat()
                currentMaxDockApps = sharedPreferences.getInt(FloatingActionService.KEY_MAX_DOCK_APPS, 4).toFloat()
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
                FloatingActionService.KEY_SELECTED_APPS -> {
                    selectedAppCount = prefs.getStringSet(key, emptySet())?.size ?: 0
                }
                FloatingActionService.KEY_FLOATING_ALPHA -> {
                    currentAlpha = prefs.getFloat(key, 1.0f)
                }
                FloatingActionService.KEY_FLOATING_ICON_SIZE -> {
                    currentIconSizeDp = prefs.getInt(key, 48).toFloat()
                }
                FloatingActionService.KEY_MAX_DOCK_APPS -> {
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
                            putFloat(FloatingActionService.KEY_FLOATING_ALPHA, currentAlpha)
                        }
                        if (FloatingActionService.isServiceRunning) {
                            val intent = Intent(context, FloatingActionService::class.java).apply {
                                action = FloatingActionService.ACTION_REFRESH_FLOATING_VIEW
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
                            putInt(FloatingActionService.KEY_FLOATING_ICON_SIZE, currentIconSizeDp.roundToInt())
                        }
                        if (FloatingActionService.isServiceRunning) {
                            val intent = Intent(context, FloatingActionService::class.java).apply {
                                action = FloatingActionService.ACTION_REFRESH_FLOATING_VIEW
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
                            putInt(FloatingActionService.KEY_MAX_DOCK_APPS, currentMaxDockApps.roundToInt())
                        }
                        if (FloatingActionService.isServiceRunning) {
                            val intent = Intent(context, FloatingActionService::class.java).apply {
                                action = FloatingActionService.ACTION_REFRESH_FLOATING_VIEW
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
                if (FloatingActionService.isServiceRunning) {
                    Log.d(
                        "SettingsActivity",
                        "SettingsScreen ON_RESUME, service running, sending SUPPRESS_UI intent."
                    )
                    val intent = Intent(context, FloatingActionService::class.java).apply {
                        action = FloatingActionService.ACTION_SUPPRESS_UI
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
            if (FloatingActionService.isServiceRunning) {
                val intent = Intent(context, FloatingActionService::class.java).apply {
                    action = FloatingActionService.ACTION_UNSUPPRESS_UI
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


@Composable
fun SettingsScreenContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val sharedPreferences = remember {
        context.getSharedPreferences(FloatingActionService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var selectedApps by remember {
        mutableStateOf(
            sharedPreferences.getStringSet(
                FloatingActionService.KEY_SELECTED_APPS, emptySet()
            ) ?: emptySet()
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlySelected by remember { mutableStateOf(false) }

    // Read the max dock apps limit from SharedPreferences
    val maxDockApps = sharedPreferences.getInt(FloatingActionService.KEY_MAX_DOCK_APPS, 4)

    val allLaunchableApps = remember {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val myPackageName = context.packageName
        packageManager.queryIntentActivities(mainIntent, 0).mapNotNull {
            try {
                val appInfo = packageManager.getApplicationInfo(it.activityInfo.packageName, 0)
                AppEntry(
                    packageName = appInfo.packageName,
                    label = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo)
                )
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.filter { it.packageName != myPackageName }.distinctBy { it.packageName }
            .sortedBy { it.label }
    }

    val filteredApps = remember(searchQuery, allLaunchableApps, showOnlySelected, selectedApps) {
        val appsAfterSearch = if (searchQuery.isBlank()) {
            allLaunchableApps
        } else {
            allLaunchableApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }

        if (showOnlySelected) {
            appsAfterSearch.filter { selectedApps.contains(it.packageName) }
        }
        else {
            appsAfterSearch
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search by app name") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search Icon") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

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

        if (allLaunchableApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No launchable applications were found on this device.",
                    textAlign = TextAlign.Center,
                )
            }
        } else if (filteredApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val text = if (showOnlySelected) {
                    if (selectedApps.isEmpty()) {
                        "No apps are selected. Disable the filter or select some apps."
                    } else if (searchQuery.isNotBlank()) {
                        "No selected apps match your search: \"$searchQuery\""
                    } else {
                        "No selected apps found."
                    }
                } else {
                    if (searchQuery.isNotBlank()) {
                        "No applications found matching your search: \"$searchQuery\""
                    } else {
                        // This case should ideally not be reached if allLaunchableApps is not empty.
                        "No applications found."
                    }
                }
                Text(text, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppListItem(
                        app = app,
                        isSelected = selectedApps.contains(app.packageName),
                        onSelectionChanged = { packageName, isSelected -> // `isSelected` here is the NEW state
                            val currentSelection = selectedApps.toMutableSet()
                            if (isSelected) { // Trying to add an app
                                if (currentSelection.size >= maxDockApps) {
                                    Toast.makeText(
                                        context,
                                        "Maximum of $maxDockApps apps already selected. Please deselect an app first.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@AppListItem // Do not proceed to add
                                }
                                currentSelection.add(packageName)
                            } else { // Trying to remove an app
                                currentSelection.remove(packageName)
                            }
                            selectedApps = currentSelection

                            sharedPreferences.edit(commit = true) {
                                putStringSet(
                                    FloatingActionService.KEY_SELECTED_APPS, currentSelection
                                )
                            }
                            Log.d(
                                "SettingsActivity",
                                "Committed to SharedPreferences. Selection: $currentSelection. Is empty: ${currentSelection.isEmpty()}."
                            )

                            if (FloatingActionService.isServiceRunning) {
                                val serviceIntent =
                                    Intent(context, FloatingActionService::class.java).apply {
                                        action = FloatingActionService.ACTION_REFRESH_FLOATING_VIEW
                                    }
                                context.startService(serviceIntent)
                                Log.d(
                                    "SettingsActivity",
                                    "Service running, sent refresh intent to service."
                                )
                            } else {
                                Log.d(
                                    "SettingsActivity",
                                    "Service not running, refresh intent NOT sent."
                                )
                            }
                        })
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

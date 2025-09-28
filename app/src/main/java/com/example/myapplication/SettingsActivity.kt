package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit // Added for SharedPreferences KTX
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.accompanist.drawablepainter.rememberDrawablePainter
// No longer need java.util.ArrayList

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsNavigator()
                }
            }
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

    var isServiceEnabled by remember { mutableStateOf(FloatingActionService.isServiceRunning) }

    lateinit var notificationPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            Log.d("SettingsActivity", "Overlay permission granted.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startServiceLogic(context) { isServiceEnabled = it }
            }
        } else {
            Log.d("SettingsActivity", "Overlay permission NOT granted.")
            isServiceEnabled = false
            Toast.makeText(context, "Overlay permission is required to display the app switcher.", Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, "Notification permission is required for the App Switcher service.", Toast.LENGTH_LONG).show()
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Enable App Switcher",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = isServiceEnabled,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            if (!Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                overlayPermissionLauncher.launch(intent)
                            }
                            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            else {
                                startServiceLogic(context) { newState -> isServiceEnabled = newState }
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
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Selected Applications",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Select applications"
                )
            }
        }
    }
}

private fun startServiceLogic(context: Context, updateSwitchState: (Boolean) -> Unit) {
    val hasOverlay = Settings.canDrawOverlays(context)
    val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    if (hasOverlay && hasNotification) {
        Log.d("SettingsActivity", "All permissions granted. Starting service.")
        startFloatingActionService(context)
        updateSwitchState(true)
    } else {
        Log.w("SettingsActivity", "startServiceLogic called but permissions are missing. Overlay: $hasOverlay, Notification: $hasNotification")
        updateSwitchState(false)
        if (!hasOverlay) Toast.makeText(context, "Overlay permission is required.", Toast.LENGTH_SHORT).show()
        if (!hasNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Notification permission is required.", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Applications") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
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
        mutableStateOf(sharedPreferences.getStringSet(FloatingActionService.KEY_SELECTED_APPS, emptySet()) ?: emptySet())
    }

    val launchableApps = remember {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val myPackageName = context.packageName // Get app's own package name
        packageManager.queryIntentActivities(mainIntent, 0).mapNotNull {
            try {
                val appInfo = packageManager.getApplicationInfo(it.activityInfo.packageName, 0)
                AppEntry(
                    packageName = appInfo.packageName,
                    label = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo)
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
        .filter { it.packageName != myPackageName } // Filter out the app itself
        .distinctBy { it.packageName }
        .sortedBy { it.label }
    }

    if (launchableApps.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No launchable applications were found on this device.")
        }
    } else {
        LazyColumn(modifier = modifier.padding(16.dp)) {
            items(launchableApps, key = { it.packageName }) { app ->
                AppListItem(
                    app = app,
                    isSelected = selectedApps.contains(app.packageName),
                    onSelectionChanged = { packageName, isSelected ->
                        val currentSelection = selectedApps.toMutableSet()
                        if (isSelected) {
                            currentSelection.add(packageName)
                        } else {
                            currentSelection.remove(packageName)
                        }
                        selectedApps = currentSelection
                        
                        // Save to SharedPreferences synchronously
                        sharedPreferences.edit(commit = true) { // KTX for synchronous commit
                            putStringSet(FloatingActionService.KEY_SELECTED_APPS, currentSelection)
                        }
                        // Log after committing to SharedPreferences
                        Log.d("SettingsActivity", "Committed to SharedPreferences. Selection: $currentSelection. Is empty: ${currentSelection.isEmpty()}.")

                        val serviceIntent = Intent(context, FloatingActionService::class.java).apply {
                            action = FloatingActionService.ACTION_REFRESH_FLOATING_VIEW
                            // No longer adding EXTRA_SELECTED_APPS
                        }
                        context.startService(serviceIntent)
                        // Log after sending the intent
                        Log.d("SettingsActivity", "Sent refresh intent to service.")
                    }
                )
            }
        }
    }
}


@Composable
fun AppListItem(
    app: AppEntry,
    isSelected: Boolean,
    onSelectionChanged: (String, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChanged(app.packageName, !isSelected) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = app.icon),
            contentDescription = "${app.label} icon",
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = app.label,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onSelectionChanged(app.packageName, it) }
        )
    }
}

data class AppEntry(val packageName: String, val label: String, val icon: Drawable)

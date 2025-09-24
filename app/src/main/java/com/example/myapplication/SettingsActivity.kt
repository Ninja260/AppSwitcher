package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
// import androidx.compose.material.icons.filled.ArrowBack // Remove if unused
// import androidx.compose.material.icons.filled.ArrowForward // Remove if unused
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme

// Data class to hold app information
data class AppEntry(val packageName: String, val label: String)

// SharedPreferences constants
private const val PREFS_NAME = "app_switcher_prefs"
private const val KEY_SELECTED_APPS = "selected_app_packages"
private const val KEY_APP_SWITCHER_ENABLED = "app_switcher_enabled"

// Helper function to get SharedPreferences instance
private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

// Enum to define the screens
private enum class Screen {
    Main,
    AppSelection
}

@OptIn(ExperimentalMaterial3Api::class)
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf(Screen.Main) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when (currentScreen) {
                                        Screen.Main -> "App Switcher Settings"
                                        Screen.AppSelection -> "Select Applications"
                                    }
                                )
                            },
                            navigationIcon = {
                                if (currentScreen == Screen.AppSelection) {
                                    IconButton(onClick = { currentScreen = Screen.Main }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentScreen) {
                            Screen.Main -> MainSettingsScreen(
                                onNavigateToAppSelection = { currentScreen = Screen.AppSelection }
                            )
                            Screen.AppSelection -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainSettingsScreen(onNavigateToAppSelection: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { getPrefs(context) }

    var isSwitcherEnabled by remember {
        mutableStateOf(prefs.getBoolean(KEY_APP_SWITCHER_ENABLED, true))
    }

    LaunchedEffect(isSwitcherEnabled, context) {
        prefs.edit().putBoolean(KEY_APP_SWITCHER_ENABLED, isSwitcherEnabled).apply()
        val serviceIntent = Intent(context, FloatingActionService::class.java)
        if (isSwitcherEnabled) {
            try {
                context.startService(serviceIntent)
                Log.d("MainSettingsScreen", "FloatingActionService explicitly started via switch.")
            } catch (e: Exception) {
                Log.e("MainSettingsScreen", "Error starting FloatingActionService: ${e.message}", e)
            }
        } else {
            context.stopService(serviceIntent)
            Log.d("MainSettingsScreen", "FloatingActionService explicitly stopped via switch.")
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Enable App Switcher", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = isSwitcherEnabled,
                onCheckedChange = { isSwitcherEnabled = it }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToAppSelection)
                .padding(vertical = 16.dp), // Increased padding for better touch target
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Selected Applications", style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go to app selection")
        }
    }
}


@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val prefs = remember { getPrefs(context) }

    val applications = remember {
        Log.d("SettingsScreen", "Fetching installed applications...")
        val appInfos = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        Log.d("SettingsScreen", "Total appInfos fetched: ${appInfos.size}")

        val launchableAppEntries = appInfos.filter { appInfo ->
            packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
        }.map { appInfo ->
            val label = try {
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                Log.w("SettingsScreen", "Failed to get label for ${appInfo.packageName}", e)
                appInfo.packageName // Fallback to packageName if label fails
            }
            AppEntry(packageName = appInfo.packageName, label = label)
        }.sortedBy { it.label } // Already sorted alphabetically by label here

        Log.d("SettingsScreen", "Number of launchable app entries: ${launchableAppEntries.size}")
        launchableAppEntries
    }

    var selectedPackageNames by remember {
        mutableStateOf(prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet())
    }

    LaunchedEffect(selectedPackageNames, context) {
        Log.d("SettingsScreen", "Saving selected apps: $selectedPackageNames")
        with(prefs.edit()) {
            putStringSet(KEY_SELECTED_APPS, selectedPackageNames)
            apply()
        }
        Log.d("SettingsScreen", "Sending intent to FloatingActionService to refresh: ${FloatingActionService.ACTION_REFRESH_FLOATING_VIEW}")
        val serviceIntent = Intent(context, FloatingActionService::class.java).apply {
            action = FloatingActionService.ACTION_REFRESH_FLOATING_VIEW
        }
        context.startService(serviceIntent)
    }

    // MODIFIED: No longer partitioning selected and unselected.
    // The `applications` list is already sorted alphabetically.
    // The `SettingsScreenContent` will just display this list.
    // The visual distinction of selected items comes from the Checkbox.
    val displayApplications = applications // CHANGED

    SettingsScreenContent(
        applications = displayApplications, // CHANGED
        selectedPackageNames = selectedPackageNames,
        onSelectionChanged = { packageName, isSelected ->
            selectedPackageNames = if (isSelected) {
                selectedPackageNames + packageName
            } else {
                selectedPackageNames - packageName
            }
        }
    )
}

@Composable
fun SettingsScreenContent(
    applications: List<AppEntry>,
    selectedPackageNames: Set<String>,
    onSelectionChanged: (String, Boolean) -> Unit
) {
    if (applications.isEmpty()) {
        Text("No launchable applications were found on this device.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = applications, key = { appEntry -> appEntry.packageName }) { appEntry ->
                val isSelected = appEntry.packageName in selectedPackageNames

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectionChanged(appEntry.packageName, !isSelected)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                           onSelectionChanged(appEntry.packageName, checked)
                        }
                    )
                    Text(
                        text = appEntry.label,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) 
@Preview(showBackground = true)
@Composable
fun SettingsActivityPreview_Main() {
    MyApplicationTheme {
        var currentScreen by remember { mutableStateOf(Screen.Main) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (currentScreen == Screen.Main) "App Switcher Settings" else "Select Applications") },
                    navigationIcon = {
                        if (currentScreen == Screen.AppSelection) {
                            IconButton(onClick = { currentScreen = Screen.Main }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Surface(modifier = Modifier.padding(paddingValues)) {
                when (currentScreen) {
                    Screen.Main -> MainSettingsScreen(onNavigateToAppSelection = { currentScreen = Screen.AppSelection })
                    Screen.AppSelection -> SettingsScreenContent(
                        applications = listOf(
                            AppEntry("com.example.app1", "App 1 (Selected)"),
                            AppEntry("com.example.app2", "App 2 (Not Selected)")
                        ),
                        selectedPackageNames = setOf("com.example.app1"),
                        onSelectionChanged = { _, _ -> }
                    )
                }
            }
        }
    }
}

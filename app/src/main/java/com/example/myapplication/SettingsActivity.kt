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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

// REMOVED: const val ACTION_REFRESH_FLOATING_VIEW = "com.example.myapplication.ACTION_REFRESH_FLOATING_VIEW"

// Helper function to get SharedPreferences instance
private fun getPrefs(context: Context): SharedPreferences {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen()
                }
            }
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
                appInfo.packageName
            }
            AppEntry(packageName = appInfo.packageName, label = label)
        }.sortedBy { it.label }

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
        Log.d("SettingsScreen", "Sending intent to FloatingActionService to refresh: ${FloatingActionService.ACTION_REFRESH_FLOATING_VIEW}") // CHANGED
        val serviceIntent = Intent(context, FloatingActionService::class.java).apply {
            action = FloatingActionService.ACTION_REFRESH_FLOATING_VIEW // CHANGED
        }
        context.startService(serviceIntent)
    }

    val sortedDisplayApplications = remember(applications, selectedPackageNames) {
        if (applications.isEmpty()) {
            emptyList()
        } else {
            val (selectedApps, unselectedApps) = applications.partition { appEntry ->
                appEntry.packageName in selectedPackageNames
            }
            selectedApps + unselectedApps
        }
    }

    SettingsScreenContent(
        applications = sortedDisplayApplications,
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

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    MyApplicationTheme {
        val previewApps = listOf(
            AppEntry("com.example.app1", "App 1"),
            AppEntry("com.example.app2", "Another Application Name"),
            AppEntry("com.example.app3", "Yet Another App"),
            AppEntry("com.example.app4", "Beta App")
        ).sortedBy { it.label }

        val selectedPreviewPackages = setOf("com.example.app1", "com.example.app4")
        val (selected, unselected) = previewApps.partition { it.packageName in selectedPreviewPackages }
        val sortedPreviewApps = selected + unselected

        SettingsScreenContent(
            applications = sortedPreviewApps,
            selectedPackageNames = selectedPreviewPackages,
            onSelectionChanged = { _, _ -> /* Do nothing in preview */ }
        )
    }
}

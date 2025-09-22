package com.example.myapplication

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

// Adjusted SettingsScreen to use the extracted content for better testability/previewability
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val packageManager = context.packageManager

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
                appInfo.packageName // Fallback to package name if label fails
            }
            AppEntry(packageName = appInfo.packageName, label = label)
        }.sortedBy { it.label } // Sort by label

        Log.d("SettingsScreen", "Number of launchable app entries: ${launchableAppEntries.size}")
        launchableAppEntries
    }

    var selectedPackageNames by remember { mutableStateOf(emptySet<String>()) }

    SettingsScreenContent(
        applications = applications,
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

// Extracted content of SettingsScreen for better previewability
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
        // Create a dummy list of AppEntry for preview purposes
        val previewApps = listOf(
            AppEntry("com.example.app1", "App 1"),
            AppEntry("com.example.app2", "Another Application Name"),
            AppEntry("com.example.app3", "Yet Another App")
        )
        // This preview won't reflect real selection logic perfectly but shows the layout
        // For a more interactive preview, you'd hoist the state or use a simpler fixed selection.
        SettingsScreenContent(applications = previewApps, selectedPackageNames = setOf("com.example.app1"), onSelectionChanged = {_, _ -> /* No-op for preview */})
    }
}

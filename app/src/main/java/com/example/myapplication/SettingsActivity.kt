package com.example.myapplication

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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

    val applications = remember {
        Log.d("SettingsScreen", "Fetching installed applications...")
        val appInfos = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        Log.d("SettingsScreen", "Total appInfos fetched: ${appInfos.size}")

        // Filter for launchable apps and then map to labels
        val launchableAppLabels = appInfos.filter { appInfo ->
            val hasLaunchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
            // For more detailed debugging, you could uncomment the following:
            // Log.v("SettingsScreen", "App: ${appInfo.packageName}, Label: ${try { packageManager.getApplicationLabel(appInfo).toString() } catch (e: Exception) { "N/A" }}, HasLaunchIntent: $hasLaunchIntent")
            hasLaunchIntent
        }.map { appInfo ->
            try {
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                Log.w("SettingsScreen", "Failed to get label for ${appInfo.packageName}", e)
                appInfo.packageName // Fallback to package name if label fails
            }
        }.sorted() // Sort them alphabetically

        Log.d("SettingsScreen", "Number of launchable app labels after filtering: ${launchableAppLabels.size}")
        // For more detailed debugging, you could uncomment the following to see the final list:
        // launchableAppLabels.forEach { label -> Log.v("SettingsScreen", "Launchable App: $label") }
        launchableAppLabels
    }

    if (applications.isEmpty()) {
        Text("No launchable applications were found on this device.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(applications) { appName ->
                Text(text = appName, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    MyApplicationTheme {
        SettingsScreen() // Preview might show empty or log errors due to lack of real PackageManager
    }
}


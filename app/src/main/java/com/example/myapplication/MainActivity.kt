package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // No direct result code to check here, Settings.canDrawOverlays needs to be re-checked
            if (Settings.canDrawOverlays(this)) {
                Log.d("MainActivity", "Overlay permission granted after returning from settings.")
                startFloatingActionService()
            } else {
                Log.d("MainActivity", "Overlay permission NOT granted after returning from settings.")
                // Optionally, show a message to the user explaining why the permission is needed
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onEnableClick = {
                            checkAndRequestOverlayPermission()
                        },
                        onGoToSettingsClick = {
                            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Log.d("MainActivity", "Overlay permission not available. Requesting...")
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            Log.d("MainActivity", "Overlay permission already available.")
            startFloatingActionService()
        }
    }

    private fun startFloatingActionService() {
        if (Settings.canDrawOverlays(this)) {
            Log.d("MainActivity", "Starting FloatingActionService.")
            val intent = Intent(this, FloatingActionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            Log.e("MainActivity", "Attempted to start service without overlay permission.")
            // This case should ideally be prevented by checkAndRequestOverlayPermission
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onEnableClick: () -> Unit,
    onGoToSettingsClick: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to App Switcher!",
            style = MaterialTheme.typography.headlineSmall, // Example styling
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onEnableClick) {
            Text("Enable Floating Action")
        }
        Button(
            onClick = onGoToSettingsClick,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Go to Settings")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MyApplicationTheme {
        MainScreen(onEnableClick = {}, onGoToSettingsClick = {})
    }
}

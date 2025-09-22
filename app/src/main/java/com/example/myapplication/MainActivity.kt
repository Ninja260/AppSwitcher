package com.example.myapplication

import android.Manifest // Added
import android.content.Intent
import android.content.pm.PackageManager // Added
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat // Added
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity" // Added

    // Launcher for the notification permission request (Android 13+)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "POST_NOTIFICATIONS permission granted.")
                startFloatingService()
            } else {
                Log.d(TAG, "POST_NOTIFICATIONS permission NOT granted.")
                // Optionally, inform the user that notifications are needed for the service
            }
        }

    // Launcher for the overlay permission request
    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Overlay permission granted after returning from settings.")
                requestNotificationPermission() // Proceed to notification permission
            } else {
                Log.d(TAG, "Overlay permission NOT granted after returning from settings.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onEnableClick = {
                            checkAndRequestOverlayPermission()
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Overlay permission not granted. Requesting...")
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            } else {
                Log.d(TAG, "Overlay permission already granted.")
                requestNotificationPermission() // Proceed to notification permission
            }
        } else {
            Log.d(TAG, "Below Android M, no overlay permission request needed via Settings.")
            requestNotificationPermission() // Proceed to notification permission
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "POST_NOTIFICATIONS permission already granted.")
                    startFloatingService()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Optionally, show an educational UI explaining why the permission is needed
                    Log.d(TAG, "Showing rationale for POST_NOTIFICATIONS permission.")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    Log.d(TAG, "Requesting POST_NOTIFICATIONS permission.")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Below Android 13, no runtime permission needed for notifications
            Log.d(TAG, "Below Android 13, no runtime POST_NOTIFICATIONS permission needed.")
            startFloatingService()
        }
    }

    private fun startFloatingService() {
        Log.d(TAG, "Attempting to start FloatingActionService.")
        val serviceIntent = Intent(this, FloatingActionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, onEnableClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to App Switcher!",
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Button(onClick = onEnableClick) {
            Text("Enable Floating Action")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MyApplicationTheme {
        MainScreen(onEnableClick = {})
    }
}


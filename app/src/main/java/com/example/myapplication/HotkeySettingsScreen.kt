package com.example.myapplication

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.navigation.NavController

// Enum to represent the modifier keys for clarity and type safety
enum class ModifierKey(val keyCode: Int, val readableName: String) {
    ALT(KeyEvent.KEYCODE_ALT_LEFT, "Alt"),
    CTRL(KeyEvent.KEYCODE_CTRL_LEFT, "Ctrl"),
    SHIFT(KeyEvent.KEYCODE_SHIFT_LEFT, "Shift"),
    SUPER(KeyEvent.KEYCODE_META_LEFT, "Super/Win");

    companion object {
        fun fromKeyCode(keyCode: Int): ModifierKey {
            return entries.find { it.keyCode == keyCode } ?: ALT // Default to Alt
        }
    }
}

// A generic row for displaying a setting, providing consistent styling
@Composable
fun PreferenceRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(text = summary, fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// A composable for selecting the main trigger modifier key
@Composable
fun ModifierKeyPreference(title: String, selectedKey: ModifierKey, onKeySelected: (ModifierKey) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    PreferenceRow(
        title = title,
        summary = "Double-press the selected key to activate hotkeys. Currently: ${selectedKey.readableName}",
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    ModifierKey.entries.forEach { key ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    onKeySelected(key)
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = (key == selectedKey),
                                onClick = { 
                                    onKeySelected(key)
                                    showDialog = false
                                }
                            )
                            Text(text = key.readableName, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Helper function to get a readable name from a keycode
fun getReadableKeyCodeName(keyCode: Int): String {
    if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return "Not Set"
    // KeyEvent.keyCodeToString returns "KEYCODE_A", so we remove the prefix
    return KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')
}

// A composable for selecting an action key (for launching an app)
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ActionKeyPreference(
    title: String,
    currentKeyCode: Int,
    onKeySelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    PreferenceRow(
        title = title,
        summary = "Currently: ${getReadableKeyCodeName(currentKeyCode)}",
        onClick = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Assign Hotkey for $title") },
            text = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                onKeySelected(keyEvent.nativeKeyEvent.keyCode)
                                showDialog = false
                                true // Consume the event
                            } else {
                                false // Don't consume up events
                            }
                        }
                ) {
                    Text("Press any key to assign it...")
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    onKeySelected(KeyEvent.KEYCODE_UNKNOWN) // Clear the key
                    showDialog = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotkeySettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences(ComposeFloatingActionService.PREFS_NAME, Context.MODE_PRIVATE)
    }

    // State for the trigger modifier key, defaulting to Alt
    var triggerModifier by remember {
        val savedKeyCode = sharedPreferences.getInt(ComposeFloatingActionService.KEY_HOTKEY_TRIGGER_MODIFIER, KeyEvent.KEYCODE_ALT_LEFT)
        mutableStateOf(ModifierKey.fromKeyCode(savedKeyCode))
    }

    // State for the action keys for the 4 dock apps
    val actionKeyCodes = remember {
        mutableStateListOf(
            sharedPreferences.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_1, KeyEvent.KEYCODE_1),
            sharedPreferences.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_2, KeyEvent.KEYCODE_2),
            sharedPreferences.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_3, KeyEvent.KEYCODE_3),
            sharedPreferences.getInt(ComposeFloatingActionService.KEY_HOTKEY_ACTION_4, KeyEvent.KEYCODE_4)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hotkey Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            item {
                ModifierKeyPreference(
                    title = "Trigger Key",
                    selectedKey = triggerModifier,
                    onKeySelected = { newKey ->
                        triggerModifier = newKey
                        sharedPreferences.edit {
                            putInt(ComposeFloatingActionService.KEY_HOTKEY_TRIGGER_MODIFIER, newKey.keyCode)
                        }
                    }
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                Text("Action Keys", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            }

            itemsIndexed(actionKeyCodes) { index, keyCode ->
                ActionKeyPreference(
                    title = "Dock App ${index + 1}",
                    currentKeyCode = keyCode,
                    onKeySelected = { newKeyCode ->
                        actionKeyCodes[index] = newKeyCode
                        sharedPreferences.edit {
                            val key = when(index) {
                                0 -> ComposeFloatingActionService.KEY_HOTKEY_ACTION_1
                                1 -> ComposeFloatingActionService.KEY_HOTKEY_ACTION_2
                                2 -> ComposeFloatingActionService.KEY_HOTKEY_ACTION_3
                                else -> ComposeFloatingActionService.KEY_HOTKEY_ACTION_4
                            }
                            putInt(key, newKeyCode)
                        }
                    }
                )
            }
        }
    }
}

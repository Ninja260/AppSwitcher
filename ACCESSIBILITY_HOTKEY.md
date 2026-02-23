# Feature: Customizable Global Hotkey App Launching

This document outlines the plan to implement a system-wide hotkey feature using an Accessibility Service. This will allow users to launch docked apps and switch between recent apps using keyboard shortcuts, even when the App Switcher application is not in the foreground.

## Phase 1: Core Hotkey Functionality (MVP)

1.  **Create `HotkeyService.kt`:**
    *   Create a new class `HotkeyService` that extends `android.accessibilityservice.AccessibilityService`.
    *   Implement the `onKeyEvent()` method to receive global key events.
    *   Implement `onAccessibilityEvent()` to track window state changes (for Phase 3).

2.  **Configure the Accessibility Service:**
    *   Create a new XML resource file at `res/xml/accessibility_service_config.xml`.
    *   In this file, configure the service to request key events (`FLAG_REQUEST_FILTER_KEY_EVENTS`) and specific event types (`typeViewKeyPressed`, `typeViewKeyReleased`, `typeWindowStateChanged`).

3.  **Update `AndroidManifest.xml`:**
    *   Add a new `<service>` declaration for `HotkeyService`.
    *   Protect the service with the `android.permission.BIND_ACCESSIBILITY_SERVICE` permission.
    *   Add a `<meta-data>` tag within the service declaration to point to the `@xml/accessibility_service_config` file.

4.  **Implement Default Hotkey Logic:**
    *   Inside `onKeyEvent()`, implement logic to detect `Super + <Number>` key combinations (from 1 to 4).
    *   The service will read the ordered list of docked apps from `SharedPreferences`.
    *   Upon detecting a valid hotkey, it will launch the corresponding app from the list.
    *   The key event must be consumed to prevent it from propagating to the underlying application.

5.  **Create Basic UI for Enabling the Service:**
    *   In `SettingsActivity.kt` (`MainSettingsScreen`), add a new section for "Hotkey Launching."
    *   This UI will display the status of the Accessibility Service (enabled or disabled).
    *   It will include a button that launches an intent with `Settings.ACTION_ACCESSIBILITY_SETTINGS`, taking the user directly to the system screen where they can manually enable the service for the app.

## Phase 2: UI for Hotkey Customization

1.  **Data Storage:**
    *   Define new string keys in `SharedPreferences` (e.g., in `ComposeFloatingActionService.kt` companion object) to store custom hotkeys.
    *   Keys will be needed for each of the 4 dockable apps (e.g., `KEY_HOTKEY_APP_1`) and for the "last app switch" feature (`KEY_HOTKEY_LAST_APP`).
    *   The hotkey will be stored in a parseable string format (e.g., `"META-SHIFT-A"`).

2.  **Create `HotkeySettingsScreen.kt`:**
    *   Create a new composable screen for managing hotkey assignments.
    *   Add navigation to this screen from the `MainSettingsScreen`.

3.  **Implement Customization UI:**
    *   The `HotkeySettingsScreen` will list all 5 configurable shortcuts (4 docks + 1 last app).
    *   Each list item will display the currently assigned shortcut.
    *   A "Change" button next to each item will open a dialog. This dialog will listen for the next key combination pressed by the user and save it to `SharedPreferences`.
    *   A "Clear" button will be provided to remove the hotkey assignment for an item.

4.  **Update `HotkeyService`:**
    *   Modify the service to load the custom hotkey configurations from `SharedPreferences` on startup.
    *   The logic in `onKeyEvent()` will be updated to check for matches against these custom hotkeys instead of the hard-coded defaults.

## Phase 3: "Last Two Apps" Switching Logic

1.  **Track App History:**
    *   In the `onAccessibilityEvent()` method of `HotkeyService`, filter for events of `TYPE_WINDOW_STATE_CHANGED`.
    *   When a new app window comes to the foreground, get its package name.

2.  **Store Recent Apps:**
    *   Implement a simple, in-memory, last-in-first-out (LIFO) list/stack to store the last two unique application package names that have been in the foreground.

3.  **Implement Switch Action:**
    *   When the hotkey assigned to "Last App Switch" is detected in `onKeyEvent()`, the service will retrieve the second package name from the recent apps list and launch it.

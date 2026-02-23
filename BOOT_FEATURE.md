# "Start Service on Boot" Feature Implementation Plan

This document outlines the steps required to make the App Switcher service start automatically when the device boots up, if it was enabled by the user.

## 1. Add Permission to AndroidManifest.xml

The application needs to request the `RECEIVE_BOOT_COMPLETED` permission to be notified when the device has finished booting.

-   **Action:** Add the following line inside the `<manifest>` tag in `app/src/main/AndroidManifest.xml`:
    ```xml
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    ```

## 2. Create BootCompletedReceiver.kt

A `BroadcastReceiver` is required to listen for the boot system event.

-   **Action:** Create a new Kotlin file named `BootCompletedReceiver.kt` in the `com.example.myapplication` package.

## 3. Register the Receiver in AndroidManifest.xml

The new receiver must be registered in the manifest so the Android system knows it exists and what event it listens for.

-   **Action:** Add the following `<receiver>` block inside the `<application>` tag in `app/src/main/AndroidManifest.xml`:
    ```xml
    <receiver
        android:name=".BootCompletedReceiver"
        android:enabled="true"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
        </intent-filter>
    </receiver>
    ```

## 4. Implement Receiver Logic

The receiver needs to check if the service should be started.

-   **Action:** Inside `BootCompletedReceiver.kt`, implement the `onReceive` method. This method will:
    1.  Access `SharedPreferences`.
    2.  Check for a specific boolean flag (e.g., `KEY_SERVICE_ENABLED`).
    3.  If the flag is `true`, it will start the `ComposeFloatingActionService`.

## 5. Update SettingsActivity to Save Enabled State

The "Enable App Switcher" switch must persist its state so the `BootCompletedReceiver` can read it.

-   **Action:** In `MainSettingsScreen.kt` (or the relevant settings composable), modify the `onCheckedChange` lambda for the `Switch`.
    1.  When the switch is toggled, save the `true`/`false` state to `SharedPreferences` using the new `KEY_SERVICE_ENABLED` key.

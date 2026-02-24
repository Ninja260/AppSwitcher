# Product Requirements Document: App Switcher

## 1. Introduction

**App Name:** App Switcher

**Brief Description:** An Android application designed to provide users with a quick and convenient way to switch between selected applications using a persistent, customizable floating action interface and global keyboard shortcuts.

## 2. Goals

*   Enable users to quickly launch and switch between their frequently used applications.
*   Provide a customizable and easily accessible floating interface for app shortcuts.
*   Offer a robust global hotkey system for keyboard-centric users to switch apps from anywhere.
*   Minimize the steps and time required for users to switch applications.
*   Offer a user-friendly way to manage and customize the experience.

## 3. Target Audience

*   Android users who frequently multitask and switch between several applications.
*   Users looking for a faster, more direct alternative to standard Android app switching methods.
*   Power users who want to customize their app access experience, including keyboard-first users.

## 4. Key Features

### 4.1. Floating Action Interface
*   **Persistent Overlay:** The app displays a floating action element that remains visible on top of other applications and the system launcher.
*   **Drag and Snap:** Users can drag the floating action vertically and, upon release, it will snap to the left or right edge of the screen.
*   **Minimizable:** The floating action can be minimized to a small icon to save screen space and expanded to show the app dock.
*   **Customizable Appearance:** Users can adjust the transparency (alpha) and the size of the icons within the floating action.

### 4.2. Customizable App Shortcuts
*   **User-Defined App List:** Users can select and add a specific set of applications to be displayed as shortcuts in the floating action dock.
*   **Configurable Dock Size:** Users can configure the maximum number of apps to display in the dock (from 2 to 4).
*   **App Management:** An intuitive settings screen allows users to browse, search, and select/deselect applications for the dock.

### 4.3. Quick App Launching
*   **Single-Tap Switching:** Tapping an app icon within the floating action interface immediately launches the corresponding application or brings it to the foreground if it's already running.

### 4.4. Global Hotkey System
*   **Accessibility Service:** Utilizes an Accessibility Service to listen for global key presses, allowing hotkeys to work outside the app.
*   **Configurable Trigger Key:** Users can select a trigger modifier key (e.g., Alt, Ctrl, Shift) from the settings.
*   **Double-Press Activation:** The hotkey sequence is activated by double-pressing the selected trigger key.
*   **Customizable Action Keys:** Users can assign a unique keyboard key to each of the four dock app slots for quick launching.

## 5. User Stories

*   As a user, I want to add my most used apps to a floating panel so I can access them instantly.
*   As a user, I want to tap an icon on the floating panel to quickly switch to that app.
*   As a user, I want to configure global hotkeys (e.g., `Alt+Alt, J`) to launch my favorite apps without touching the screen.
*   As a user, I want to customize which key I use to trigger the hotkeys so it doesn't conflict with other apps.
*   As a user, I want to change the transparency and size of the floating action to make it less intrusive.
*   As a user, I want the app to start automatically when my device boots up.

## 6. Non-Functional Requirements

*   **Performance:** The floating interface and accessibility service should be responsive and not introduce lag to the system.
*   **Permissions:** The app will require "Display over other apps" and "Accessibility Service" permissions. The permission request process must be clear and explain why each is needed.
*   **Battery Usage:** The app should be optimized to minimize battery consumption.
*   **Usability:** The interface for all settings should be intuitive and simple.

## 7. Future Considerations (Out of Scope for V1)

*   **"Last App" Switching:** A dedicated hotkey to switch between the two most recent applications.
*   **Advanced Appearance Customization:** More options for the floating action's appearance (e.g., color, different icon styles).
*   **Advanced Layouts:** Support for different floating action layouts (e.g., grid, list, radial menu).
*   **Gesture Control:** Gesture-based activation or deactivation of the floating interface.
*   **Shortcuts to App Actions:** Support for launching specific app actions (e.g., compose a new email in a mail app).


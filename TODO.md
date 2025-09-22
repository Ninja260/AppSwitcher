# App Switcher - TODO List

This document outlines the development tasks required to build the App Switcher application based on the [PRD.md](PRD.md). Refer to [NOTICE.md](NOTICE.md) for important considerations regarding specific Android features and APIs.

## Development Workflow

For each item in this TODO list:
1.  The AI assistant (Gemini) will assist with the implementation of the item.
2.  The user will manually test the implemented functionality.
3.  The user will decide if the item is complete and satisfactory ("done"). **Tasks will only be marked as done upon explicit user approval.**
4.  Once an item is marked as done, the AI assistant will suggest the user make a Git commit for the changes related to that item.
5.  We will then proceed to the next item in the list.

## Phase 1: Core Functionality (MVP)

*   **[X] 1. Project Setup & Basic Structure**
    *   [X] 1.1. Confirm target Android SDK versions. (See NOTICE.md for implications)
    *   [X] 1.2. Set up necessary permissions in `AndroidManifest.xml` (e.g., `SYSTEM_ALERT_WINDOW`). (See NOTICE.md)
*   **[ ] 2. Floating Action Service**
    *   [X] 2.1. Create a foreground service to manage the floating action. (See NOTICE.md regarding foreground service requirements)
    *   [X] 2.2. Implement logic to request "Display over other apps" permission from the user. (See NOTICE.md regarding SYSTEM_ALERT_WINDOW)
    *   [X] 2.3. Design and implement the basic visual appearance of the floating action (e.g., a simple button or small panel).
*   **[ ] 3. App Selection & Storage**
    *   [X] 3.1. Create a basic settings/configuration screen (Activity or Composable).
    *   [X] 3.2. Implement functionality to list all <caret>installed applications on the device. (See NOTICE.md for critical package visibility considerations)
    *   [X] 3.3. Allow users to select multiple apps from the list.
    *   [X] 3.4. Store the list of user-selected apps persistently (e.g., using SharedPreferences or a simple database).
    *   [ ] 3.5. Show the selected apps at the top of the list in the settings screen.
*   **[ ] 4. Displaying App Shortcuts in Floating Action**
    *   [X] 4.1. The floating action service should read the saved list of selected apps.
    *   [X] 4.2. Dynamically display icons of the selected apps within the floating action interface.
    *   [X] 4.3. Ensure icons are clear and identifiable.
*   **[ ] 5. App Switching Logic**
    *   [X] 5.1. Implement the functionality to launch an app (or bring to foreground if already running) when its icon is tapped in the floating action.
    *   [X] 5.2. If an app fails to launch (e.g., uninstalled), remove it from the floating action and persistent storage.
    *   [ ] 5.3. Test thoroughly on various Android versions and with different apps.

## Phase 2: UI/UX Enhancements & Polish

*   **[ ] 6. Floating Action Customization (Basic)**
    *   [ ] 6.1. Allow users to move the floating action element on the screen.
    *   [ ] 6.2. (Optional) Implement docking to screen edges.
*   **[ ] 7. App List Management in Settings**
    *   [ ] 7.1. Allow users to remove apps from the selected list.
    *   [ ] 7.2. (Optional) Allow users to reorder the apps in the list (this order would then reflect in the floating action).
    *   [ ] 7.3. Improve the UI of the app selection screen for better usability.
*   **[ ] 8. User Experience & Onboarding**
    *   [ ] 8.1. Provide clear instructions on how to grant necessary permissions.
    *   [ ] 8.2. Create a simple first-launch experience or tutorial.

## Phase 3: Non-Functional Requirements & Testing

*   **[ ] 9. Performance Optimization**
    *   [ ] 9.1. Profile and optimize the floating service for minimal CPU and memory usage.
    *   [ ] 9.2. Ensure smooth animations and interactions for the floating action.
*   **[ ] 10. Battery Usage Optimization**
    *   [ ] 10.1. Analyze and minimize battery consumption by the foreground service. (See NOTICE.md regarding foreground service requirements)
*   **[ ] 11. Robustness and Error Handling**
    *   [ ] 11.1. Handle cases where selected apps are uninstalled.
    *   [ ] 11.2. Ensure the floating action behaves correctly across device reboots.
*   **[ ] 12. Testing**
    *   [ ] 12.1. Unit tests for core logic (app selection, storage, launching).
    *   [ ] 12.2. UI tests for the settings screen and floating action interactions.
    *   [ ] 12.3. Manual testing on different devices and Android versions.

## Phase 4: Future Considerations (Post V1 Launch)

*   [ ] Explore advanced customization options (appearance, size, transparency).
*   [ ] Investigate support for shortcuts to specific app actions.
*   [ ] Consider different layouts for the floating action (grid, list, radial menu).
*   [ ] Research gesture-based activation/deactivation.

---

**Notes:**
*   Prioritize tasks in Phase 1 to get a Minimum Viable Product (MVP).
*   Regularly test on actual devices.
*   Keep the PRD in mind for all feature implementations.


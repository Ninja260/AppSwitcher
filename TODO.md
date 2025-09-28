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
*   **[X] 2. Floating Action Service**
    *   [X] 2.1. Create a foreground service to manage the floating action. (See NOTICE.md regarding foreground service requirements)
    *   [X] 2.2. Implement logic to request "Display over other apps" permission from the user. (See NOTICE.md regarding SYSTEM_ALERT_WINDOW)
    *   [X] 2.3. Design and implement the basic visual appearance of the floating action (e.g., a simple button or small panel).
*   **[X] 3. App Selection & Storage**
    *   [X] 3.1. Create a basic settings/configuration screen (Activity or Composable).
    *   [X] 3.2. Implement functionality to list all installed applications on the device. (See NOTICE.md for critical package visibility considerations)
    *   [X] 3.3. Allow users to select multiple apps from the list.
    *   [X] 3.4. Store the list of user-selected apps persistently (e.g., using SharedPreferences or a simple database).
    *   [X] 3.5. Show the selected apps at the top of the list in the settings screen.
*   **[X] 4. Displaying App Shortcuts in Floating Action**
    *   [X] 4.1. The floating action service should read the saved list of selected apps.
    *   [X] 4.2. Dynamically display icons of the selected apps within the floating action interface.
    *   [X] 4.3. Ensure icons are clear and identifiable.
*   **[X] 5. App Switching Logic**
    *   [X] 5.1. Implement the functionality to launch an app (or bring to foreground if already running) when its icon is tapped in the floating action.
    *   [X] 5.2. If an app fails to launch (e.g., uninstalled), remove it from the floating action and persistent storage.
    *   [X] 5.3. Test thoroughly on various Android versions and with different apps.

## Phase 2: UI/UX Enhancements & Polish

*   **[X] 6. Floating Action Customization (Basic)**
    *   [X] 6.1. Allow users to move the floating action element on the screen.
    *   [X] 6.2. (Optional) Implement docking to screen edges.
*   **[X] 7. Changes to Settings Module**
    *   [X] 7.1. Allow users to remove apps from the selected list.
    *   [X] 7.2. Create a main Settings screen with global controls:
        *   [X] 7.2.1. Implement an "Enable App Switcher" switch on this main Settings screen to easily toggle the floating action service on/off.
        *   [X] 7.2.2. Add a "Selected Applications" navigation item on the main Settings screen that, when clicked, opens the app selection page.
        *   [X] 7.2.3. Provide additional description of settings on the main setting page.
          *   [X] 7.2.3.1. Description text under "Enable App Switcher" switch. If the service is running, show "App Switcher service running.", else show "App Switcher service disabled."
          *   [X] 7.2.3.2. Add description text under "Selected Application", which show the number of application selected. This text will reflect the actual selected count with real time.
    *   [X] 7.3. Enhance the App Selection Page (currently SettingsActivity):
        *   [X] 7.3.1. Modify app list display: Remove the current behavior of sorting selected apps to the top of the list (apps will appear in a single, alphabetically sorted list, filterable by search and selection status).
        *   [X] 7.3.2. Implement search functionality: Add a search bar to allow users to filter the list of installed applications by name.
        *   [X] 7.3.3. Implement filter toggle: Add a control (e.g., a filter icon button) to allow users to switch the list view between "All Apps" and "Selected Apps Only".
        *   [X] 7.3.4. General UI/UX improvements: Further refine the app selection screen for better clarity and ease of use (e.g., improving visual distinction for selected items if not covered by filters, addressed floating view suppression/unsuppression logic when navigating to/from app selection screen).
*   **[X] 8. Features for usability**
    *   **[X] 8.1. Transparency of app icons in floating action**
        *   [X] 8.1.1. Slider to adjust transparency on main setting page. The slider value will be stored to permanent storage.
        *   [X] 8.1.2. Apply transparency to app icons and the floating action parent.
        *   [X] 8.1.3. Slider transparency value will reflect to the app icons and the floating actions with real time.
    *   **[X] 8.2. Set floating action app icons size**
        *   [X] 8.2.1. Slider to adjust size on main setting page. The slider value will be stored to permanent storage.
        *   [X] 8.2.2. Apply size to app icons.
        *   [X] 8.2.3. Slider size value will reflect to the app icons on the floating actions with real time.
    *   **[X] 8.3. Set max numbers of dockabled applications**
        *   [X] 8.3.1. Slider to adjust max number of dockabled applications on main setting page. The slider value will be stored to permanent storage. The value can only be 2, 3 or 4.
        *   [X] 8.3.2. Apply max number to app icons.
        *   [X] 8.3.3. Slider max number value will reflect to the app icons on the floating actions with real time.

## Phase 3: Non-Functional Requirements & Testing

*   **[ ] 9. User Experience & Onboarding**
    *   [ ] 9.1. Provide clear instructions on how to grant necessary permissions.
    *   [ ] 9.2. Create a simple first-launch experience or tutorial.
*   **[ ] 10. Performance Optimization**
    *   [ ] 10.1. Profile and optimize the floating service for minimal CPU and memory usage.
    *   [ ] 10.2. Ensure smooth animations and interactions for the floating action.
*   **[ ] 11. Battery Usage Optimization**
    *   [ ] 11.1. Analyze and minimize battery consumption by the foreground service. (See NOTICE.md regarding foreground service requirements)
*   **[ ] 12. Robustness and Error Handling**
    *   [ ] 12.1. Handle cases where selected apps are uninstalled.
    *   [ ] 12.2. Ensure the floating action behaves correctly across device reboots.
*   **[ ] 13. Testing**
    *   [ ] 13.1. Unit tests for core logic (app selection, storage, launching).
    *   [ ] 13.2. UI tests for the settings screen and floating action interactions.
    *   [ ] 13.3. Manual testing on different devices and Android versions.

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


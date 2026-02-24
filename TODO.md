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
*   **[X] 2. Floating Action Service**
*   **[X] 3. App Selection & Storage**
*   **[X] 4. Displaying App Shortcuts in Floating Action**
*   **[X] 5. App Switching Logic**

## Phase 2: UI/UX Enhancements & Polish

*   **[X] 6. Floating Action Customization (Basic)**
*   **[X] 7. Changes to Settings Module**
*   **[X] 8. Features for usability**

## Phase 3: Refactor Floating Action to Jetpack Compose

*   **[X] 9. Initial Setup for Compose Refactoring**
*   **[X] 10. Implement the Floating Action Composable**
*   **[X] 11. Integration and Cleanup**
*   **[X] 12. Robustness and Error Handling**

## Phase 4: Global Hotkey Functionality (Accessibility Service)

*   **[X] 13. Core Hotkey Service (MVP)**
    *   [X] 13.1. Create `HotkeyService.kt` that extends `AccessibilityService`.
    *   [X] 13.2. Create `res/xml/accessibility_service_config.xml` to specify event filtering.
    *   [X] 13.3. Declare the service in `AndroidManifest.xml`.
    *   [X] 13.4. Implement the default hotkey logic in `onKeyEvent()`.
    *   [X] 13.5. Add a settings screen UI to enable the service.

## Phase 5: Advanced Hotkey Features

*   **[X] 14. Hotkey Customization UI**
    *   [X] 14.1. Define `SharedPreferences` keys for storing custom hotkeys for 4 dock apps and the "last app switch" function.
    *   [X] 14.2. Create a new `HotkeySettingsScreen.kt` for managing hotkey assignments.
    *   [X] 14.3. Implement the UI with options to display, change, and clear the hotkey for each of the 5 configurable shortcuts.
    *   [X] 14.4. Update `HotkeyService` to load and use these custom hotkeys instead of the hard-coded defaults.
*   **[X] 15. "Last Two Apps" Switching**
    *   [X] 15.1. In `HotkeyService`, implement `onAccessibilityEvent()` to track `TYPE_WINDOW_STATE_CHANGED` events and identify foreground apps.
    *   [X] 15.2. Maintain an in-memory list of the last two unique foreground applications.
    *   [X] 15.3. When the assigned hotkey is pressed, implement the logic to launch the second app from the recent apps list.

## Phase 6: Non-Functional Requirements & Testing

*   **[ ] 16. User Experience & Onboarding**
    *   [ ] 16.1. Provide clear instructions on how to grant necessary permissions (especially for Accessibility Service).
    *   [ ] 16.2. Create a simple first-launch experience or tutorial.
*   **[ ] 17. Performance Optimization**
    *   [ ] 17.1. Profile and optimize the floating service for minimal CPU and memory usage.
    *   [ ] 17.2. Ensure smooth animations and interactions for the floating action.
*   **[ ] 18. Battery Usage Optimization**
    *   [ ] 18.1. Analyze and minimize battery consumption by both the foreground and accessibility services.
*   **[ ] 19. Testing**
    *   [ ] 19.1. Unit tests for core logic (app selection, storage, launching, hotkey parsing).
    *   [`~`] 19.2. UI tests for the settings screens and floating action interactions.
    *   [ ] 19.3. Manual testing on different devices and Android versions, with physical keyboards.

## Phase 7: Future Considerations (Post V1 Launch)

*   [ ] Explore advanced customization options (appearance, size, transparency).
*   [ ] Investigate support for shortcuts to specific app actions.
*   [ ] Consider different layouts for the floating action (grid, list, radial menu).
*   [ ] Research gesture-based activation/deactivation.

---

**Notes:**
*   Prioritize tasks in Phase 1 to get a Minimum Viable Product (MVP).
*   Regularly test on actual devices.
*   Keep the PRD in mind for all feature implementations.

# Floating Action (DraggableLinearLayout) Refactoring Guide

This document lists the key features of the `DraggableLinearLayout` that must be preserved during refactoring.

## Refactoring Goal

The `DraggableLinearLayout` will be refactored to use Jetpack Compose.

## Core Features

*   **Draggable View:** The entire layout can be moved freely around the screen by dragging any part of it.

*   **Snap-to-Edge:** Upon release after dragging, the view automatically snaps to the nearest vertical edge of the screen (left or right).

*   **Position Persistence:** The view's X and Y coordinates are saved in `SharedPreferences`. When the app restarts, the view should restore its last saved position.

*   **Minimize/Expand Functionality:**
    *   A dedicated button toggles the visibility of the `appIconsContainer`.
    *   The button's icon must update to reflect the state (e.g., an up arrow for "minimize" and a down arrow for "expand").

*   **Click vs. Drag Differentiation:** The system must correctly distinguish between a click on the minimize/expand button and a drag gesture on the layout. A click should only trigger the minimize/expand action, while a drag should move the entire view.

*   **Orientation Change Handling:** The view must correctly handle screen orientation changes. If it was snapped to an edge, it should re-snap to the corresponding edge in the new orientation, adjusting its position relative to the new screen width.

*   **Dynamic Content Container:** The `appIconsContainer` must remain a `LinearLayout` (or equivalent container) that allows for dynamic addition and removal of child views (like app icons) from other parts of the application.

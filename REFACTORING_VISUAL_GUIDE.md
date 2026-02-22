# Visual Consistency Guide for Compose Refactoring

This document outlines the plan to ensure the new Jetpack Compose floating action UI is visually identical to the existing `DraggableLinearLayout` implementation.

## Commitment to Visual Parity

I will ensure that the refactored Jetpack Compose component is a pixel-perfect replica of the original view-based component. All sizes, paddings, icons, and behaviors will be meticulously recreated to maintain the current look and feel.

## Analysis of the Current UI (`DraggableLinearLayout`)

The current UI is built programmatically. Here is a breakdown of its structure and the corresponding Jetpack Compose plan:

*   **Root Container:**
    *   **Current:** A vertical `LinearLayout` with horizontally centered children.
    *   **Compose Plan:** A `Column` with `horizontalAlignment = Alignment.CenterHorizontally`.

*   **Minimize/Expand Button:**
    *   **Current:** An `ImageView` with `12.dp` padding, using `android.R.drawable.arrow_down_float` and `android.R.drawable.arrow_up_float` icons.
    *   **Compose Plan:** An `IconButton` with `Modifier.padding(12.dp)`. It will contain an `Icon` that uses equivalent vector icons or the exact same drawables via `painterResource` to ensure visual fidelity.

*   **App Icons Container:**
    *   **Current:** A vertical `LinearLayout` whose visibility is toggled between `GONE` and `VISIBLE`.
    *   **Compose Plan:** A `Column` whose visibility is managed by `AnimatedVisibility` to smoothly replicate the show/hide behavior.

By following this guide, the end user will experience no visual change after the refactoring is complete.

# Development Notices for App Switcher

This document contains important considerations and potential challenges identified during the planning and development of the App Switcher application.

## SDK Version Considerations

The project is currently configured with:
*   `compileSdk = 35`
*   `minSdk = 24` (Android 7.0 Nougat)
*   `targetSdk = 35` (Android 15)

Key implications of these versions:

1.  **`SYSTEM_ALERT_WINDOW` (Floating Action):**
    *   The `minSdk = 24` means the app targets versions where explicit user permission for drawing over other apps is standard. This is expected.
    *   With `targetSdk = 35`, we must adhere to the latest Android restrictions and best practices for overlays.

2.  **Package Visibility (Listing Installed Apps):**
    *   **Crucial Point:** Due to `targetSdk = 35` (behavior applies from Android 11/API 30+), the app will be subject to package visibility filtering.
    *   By default, the app **cannot** see all other installed applications.
    *   To implement feature 3.2 ("Implement functionality to list all installed applications on the device"), which allows users to select *any* app, the `QUERY_ALL_PACKAGES` permission will likely be required in `AndroidManifest.xml`.
    *   **Google Play Store:** If distributing via Google Play, the use of `QUERY_ALL_PACKAGES` must be justified under their allowed use cases (e.g., device search, accessibility tools, launchers). An "app switcher" might qualify, but this needs careful consideration and justification during submission.
    *   Alternatives (like using an Intent for an app picker) might be less seamless but avoid this permission. This will be evaluated when implementing feature 3.2.

3.  **Foreground Services:**
    *   Targeting `targetSdk = 35` means strict adherence to foreground service requirements (persistent notification, clearly defined purpose) is necessary for battery conservation and user transparency.

**Conclusion for Task 1.1 (Confirm target Android SDK versions):**
The current SDK versions are acceptable to proceed with, provided these implications, especially concerning package visibility, are addressed during development. No immediate change to SDK versions is mandated, but careful implementation and testing will be required for features interacting with these restricted APIs.


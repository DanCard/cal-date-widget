# Cal Date Widget

A highly customizable Android Date Widget that automatically resizes text to fit the widget dimensions.

## Features

*   **Auto-Resizing Text:** The date text automatically scales to fill the available widget space.
*   **Flexible Sizing:** Supports standard sizes and can resize down to **1x1**.
*   **Customization:**
    *   **Date Format:** Choose from presets (e.g., "EEE, MMM d") or enter your own custom `SimpleDateFormat` pattern.
    *   **Colors:** Fully customizable Text, Background, and Shadow colors with an RGB picker.
    *   **Opacity:** Adjust the background transparency.
*   **Interactive:**
    *   Tap the date to open your default **Calendar** app.
    *   Tap the **Gear Icon** (bottom-right) to configure settings.
*   **Instant Preview:** See your changes in real-time on the configuration screen.
*   **Undo Support:** Accidentally changed a color? Use the **Undo** button to revert changes (supports multiple levels of undo).
*   **Auto-Save:** Changes are applied immediately as you make them.

## Setup & Installation

1.  Clone the repository.
2.  Open the project in Android Studio.
3.  Build and run the project on your device or emulator:
    ```bash
    ./gradlew installDebug
    ```
4.  **Add the Widget:**
    *   Go to your device's home screen.
    *   Long-press on an empty space.
    *   Select **Widgets**.
    *   Find **Cal Date Widget**.
    *   Drag and drop it onto your home screen.

## Development

### Running Tests

The project includes unit tests for the preferences and state management logic.

Run unit tests via the command line:
```bash
./gradlew testDebugUnitTest
```

### Project Structure

*   `app/src/main/java/ai/dcar/caldatewidget`:
    *   `DateWidgetProvider.kt`: The main widget logic.
    *   `ConfigActivity.kt`: The configuration screen activity.
    *   `PrefsManager.kt`: Handles saving and loading widget settings.
    *   `SettingsStateManager.kt`: Manages the undo stack and temporary state.
*   `app/src/main/res/layout`:
    *   `widget_date.xml`: The widget's UI layout.
    *   `activity_config.xml`: The configuration screen layout.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

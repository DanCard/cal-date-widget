# Cal Date Widget

An Android app with three home screen widgets: a self-resizing date widget, a
daily calendar view, and a weekly calendar view.

## Widgets

*   **Date widget:** Shows the current date. Text auto-scales to fit the
    widget's dimensions, down to 1x1. Choose a preset format or enter your own
    `SimpleDateFormat` pattern.
*   **Daily calendar widget:** Shows today's events from your device
    calendars. Automatically advances to tomorrow once today's events have
    ended.
*   **Weekly calendar widget:** A 7-day view of upcoming events. Today's
    column is wider than the rest, and text size adapts to fit available
    space. Days earlier than today automatically shift forward to show the
    same weekday of *next* week, so every column stays forward-looking.

All three widgets share:
*   **Customization:** Text, background, and shadow colors with an RGB
    picker; adjustable background opacity.
*   **Interactive:** Tap a widget to open your calendar app; tap the gear icon
    to configure settings.
*   **Undo support:** Revert setting changes (multiple levels) from each
    config screen.
*   **Privacy:** No `INTERNET` permission — calendar data never leaves the
    device.

## Setup & Installation

1.  Clone the repository.
2.  Open the project in Android Studio.
3.  Build and run the project on your device or emulator:
    ```bash
    ./gradlew installDebug
    ```
4.  **Add a widget:**
    *   Go to your device's home screen.
    *   Long-press on an empty space.
    *   Select **Widgets**.
    *   Find **Cal Date Widget** — pick the Date, Daily, or Weekly widget.
    *   Drag and drop it onto your home screen.

## Development

### Running Tests

The project includes unit tests for display logic, preferences/state
management, event filtering, and deduplication.

Run unit tests via the command line:
```bash
./gradlew testDebugUnitTest
```

Run unit tests with a concise pass/fail summary:
```bash
scripts/run-unit-tests.sh
scripts/run-unit-tests.sh --task :app:testDebugUnitTest
scripts/run-unit-tests.sh --tests ai.dcar.caldatewidget.PrefsManagerTest
```

Run instrumented tests with a concise pass/fail summary (defaults to all connected emulators):
```bash
scripts/emulator-tests.sh
scripts/emulator-tests.sh --serial emulator-5554
scripts/emulator-tests.sh --class ai.dcar.caldatewidget.ExampleTest
```

### Manual Daily Widget Indicator Check

Use this script to validate the daily "tomorrow" indicator behavior from `adb` logs:

```bash
scripts/check_daily_tomorrow_indicator.sh --scenario auto
scripts/check_daily_tomorrow_indicator.sh --scenario today
```

Optional: target a specific device/emulator:

```bash
scripts/check_daily_tomorrow_indicator.sh --scenario auto --serial emulator-5554
```

### Project Structure

*   `app/src/main/java/ai/dcar/caldatewidget`:
    *   `DateWidgetProvider.kt` / `DailyWidgetProvider.kt` / `WeeklyWidgetProvider.kt`: the three widget providers.
    *   `ConfigActivity.kt` / `DailyConfigActivity.kt` / `WeeklyConfigActivity.kt`: each widget's configuration screen.
    *   `CalendarRepository.kt`: calendar data access.
    *   `WeeklyDisplayLogic.kt` / `DailyDisplayLogic.kt`: testable display calculations (text sizing, clipping, auto-advance).
    *   `CalendarImageGenerator.kt`: shared bitmap renderer for the daily and weekly widgets.
    *   `PrefsManager.kt`: saving and loading widget settings.
    *   `SettingsStateManager.kt`: manages the undo stack and temporary state.
*   `app/src/main/res/layout`:
    *   `widget_date.xml` / `widget_daily.xml` / `widget_weekly.xml`: widget UI layouts.
    *   `activity_config.xml` / `activity_daily_config.xml` / `activity_weekly_config.xml`: config screen layouts.

See `CLAUDE.md` for a deeper architecture walkthrough.

## License

This project is licensed under a Source-Available License - see the [LICENSE](LICENSE) file for details.

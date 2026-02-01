# AGENTS.md

This file provides guidance for agentic coding assistants working in this repository.

## Build & Test Commands

### Essential Commands
```bash
./gradlew assembleDebug                    # Build debug APK
./gradlew installDebug                      # Install on connected device
./gradlew clean                            # Clean build artifacts

# Unit Tests
./gradlew testDebugUnitTest                # Run all unit tests
./gradlew :app:testDebugUnitTest           # Run unit tests (alternate)
./gradlew :app:testReleaseUnitTest         # Run release unit tests
```

### Running Single Test
```bash
# Format: ./gradlew testDebugUnitTest --tests "<fully.qualified.ClassName>"
./gradlew testDebugUnitTest --tests "ai.dcar.caldatewidget.WeeklyDisplayLogicTest.shouldShowStartTimes_returns_true_when_there_is_no_clipping"
./gradlew testDebugUnitTest --tests "ai.dcar.caldatewidget.PrefsManagerTest"
```

### Android Device Testing
```bash
# Check connected devices
adb devices

# Force widget update (replace with your app package)
adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n ai.dcar.caldatewidget/.WeeklyWidgetProvider

# Capture widget screenshot
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png /tmp/widget_screenshot.png

 # View widget logs
adb logcat -s WeeklyWidget:D
adb logcat -s DailyWidget:D
adb logcat -s DateWidget:D
adb logcat -c  # Clear logs
```

### No Linting/Typechecking Commands
This project does not have configured linting or type checking commands. Do not attempt to run them.

## Git Workflow

### Committing Changes
- **Always commit all relevant changes:** Include both modified files and appropriate untracked files when creating commits
- **Before committing:** Run `git status` to review all changes, then use `git add -A` or explicitly list all relevant files
- **If unsure:** Ask the user about including untracked files (e.g., `plans/`, temporary files) before adding them
- **Test first:** Always run tests with `./gradlew testDebugUnitTest` before committing code changes

## Code Style Guidelines

### Language & Platform
- **Language:** Kotlin
- **Target SDK:** 35 (Android 15)
- **Min SDK:** 26 (Android 8.0)
- **JVM Target:** Java 21
- **Build System:** Gradle with Groovy DSL (NOT Kotlin DSL)

### Import Organization
- Imports ordered alphabetically (standard library first, then third-party, then project)
- No wildcard imports except in test files where `import org.junit.Assert.*` is acceptable
- Remove unused imports

### Formatting & Spacing
- Use 4-space indentation (no tabs)
- Blank line between methods
- One blank line before `}` closing blocks is acceptable but not required

### Naming Conventions
- **Classes:** PascalCase (e.g., `CalendarRepository`, `PrefsManager`)
- **Functions:** camelCase (e.g., `getEventsForWeek`, `loadSettings`)
- **Properties:** camelCase (e.g., `textColor`, `startTime`)
- **Constants in companion objects:** UPPER_SNAKE_CASE (e.g., `DEFAULT_TEXT_COLOR`, `PREFS_NAME`)
- **Private local constants:** UPPER_SNAKE_CASE (e.g., `val KEY_PREFIX = "widget_"`)
- **Private backing properties:** underscore prefix if needed (e.g., `_internalState`)
- **Test methods:** backtick-wrapped descriptive names (e.g., `` `shouldShowStartTimes returns true when there is no clipping` ``)

### Type System
- Use `val` for immutable references, `var` only when mutability is required
- Explicit types on public APIs, inferred types in private/internal code when obvious
- Use nullable types (`Type?`) only when null is a valid state
- Platform types from Java should be explicitly handled as nullable

### Error Handling
- Catch `SecurityException` silently when querying calendar content provider (permission not granted)
- Use `try-catch` for expected exceptional conditions, not for control flow
- Return empty lists/collections when data retrieval fails, do not throw
- No custom exception classes needed for this project

### Commenting Style
- KDoc comments (`/** */`) for public functions with parameters and return values
- Inline comments (`//`) for non-obvious logic or calculations
- Do not add comments for self-explanatory code
- No inline comments in test code except Given-When-Then structure markers

### Code Patterns
- **Data classes:** For DTOs and data containers (e.g., `CalendarEvent`, `WidgetSettings`)
- **Object declarations:** For utility classes with only static methods (e.g., `WeeklyDisplayLogic`)
- **Companion objects:** For constants and factory methods
- **When expressions:** Preferred over multiple if-else for exhaustive conditions
- **Extension functions:** Use sparingly, only when value is clear

### Testing Patterns
- Use JUnit 4 with MockK for mocking
- Structure tests with Given-When-Then comments for clarity
- Test names use backticks with descriptive behavior descriptions
- Use realistic device metrics in tests (e.g., actual pixel heights from target device)
- Test boundary conditions (exact fit, just over, just under)
- Use `assertEquals(expected, actual, delta)` for float comparisons
- Use `assertTrue(condition)` for boolean checks
- Test classes should focus on single responsibility

### Android-Specific Guidelines
- Use `context.getSharedPreferences()` for settings, not DataStore
- Use `contentResolver.query()` with `cursor?.use { }` for safe resource handling
- CalendarContract APIs require READ_CALENDAR permission (handled by user grant)
- Widget updates use `AppWidgetManager.updateAppWidget()` with `RemoteViews`
- Use `android.util.Log.d()` for debug logging with appropriate tag
- Bitmap rendering uses `Canvas` with `StaticLayout` for multi-line text
- **Widget click handling:** Use `BroadcastReceiver` (e.g., `WidgetClickReceiver`) to handle widget clicks, open calendar immediately, then schedule delayed refresh via WorkManager with battery constraints

### Critical Implementation Details
- **StaticLayout maxLines:** ALWAYS call `.setEllipsize(TextUtils.TruncateAt.END)` - without this, maxLines is ignored
- **Clipping detection:** Use conservative estimates to detect clipping before rendering (e.g., 4 lines per event with start times)
- **Equitable distribution:** When space is limited, share space equitably among items rather than first-come-first-served
- **Timezone handling:** All-day events are stored in UTC, convert to local timezone for display
- **Dynamic text sizing:** Each column independently calculates optimal font scale using binary search (0.3x to 1.5x) to fill available space
- **Past events on today:** Always 0.8x of optimal scale to make them smaller than current/future events
- **Widget creation fix:** Config activities must call `setResult(RESULT_OK, intent)` with appWidgetId for widget to appear
- **Background transparency:** Daily/Weekly widgets use 70% transparent dark background (0x4D000000) for better text contrast
- **Text shadows:** Increased shadow radius to 6f and offset to (3f, 3f) for improved readability
- **Near-duplicate filtering:** Events with same type (both all-day or both timed), start times within 15 minutes, and 5+ common words in titles are grouped together. One representative is selected using time-based rotation (changes every minute) to ensure all duplicates get shown over time. Word matching is case-insensitive and splits on whitespace and punctuation (/,-_,;:)
- **Widget click refresh:** Widget clicks go through `WidgetClickReceiver` which immediately opens calendar, then schedules a 90-second delayed refresh via WorkManager with battery-friendly constraints. This captures user edits without overwhelming the system.

### Dynamic Text Sizing Implementation
The weekly widget uses per-column dynamic font sizing to fill available space:

**Algorithm:** Binary search for optimal font scale
- Bounds: minScale=0.3f, maxScale=1.5f
- Iterations: 10 (converges quickly)
- Goal: Find largest scale where total height ≤ available height

**Method:** `calculateOptimalFontScale()` in `WeeklyWidgetProvider.kt:429-461`
- Returns optimal scale for each column independently
- Empty columns return 0.5x scale (smaller for aesthetics)
- Always shows start times (no longer toggles based on fit)

**Helper method:** `measureTotalHeightForScale()` in `WeeklyWidgetProvider.kt:463-495`
- Measures all events at given scale
- Past events on today: Additional 0.8x multiplier applied
- All other events: Direct scale

**Base text size:** 48f pixels (hardcoded, no user override)
- Event final size: 48f × optimalScale × eventScaleModifier
- Start times inherit same scale as event text

**No user-controllable text size:** Removed textSizeScale from settings
- UI slider removed from config
- Sizing is fully automatic based on content and space

### Adding New Features
1. Update `WidgetSettings` data class in `PrefsManager.kt`
2. Add save/load logic in `PrefsManager`
3. Add UI controls in config activity (`DailyConfigActivity.kt` for daily, `WeeklyConfigActivity.kt` for weekly, or `ConfigActivity.kt` for date widget)
4. Update rendering logic in widget provider if display changes
5. Add unit tests for new logic in appropriate test files
6. **For daily widget:** Implement dynamic day count based on widget width (see `calculateNumberOfDays()` and `calculateCellWidthDp()`)
7. **For daily widget:** Implement smart start day logic (today vs next day if events elapsed)
8. **For any widget:** Update AndroidManifest.xml to register new provider and config activity
9. **For any widget:** Add widget info XML file and layout files

### Widget Architecture Notes
- Three separate widgets: `DateWidgetProvider` (single date), `WeeklyWidgetProvider` (7-day view), and `DailyWidgetProvider` (dynamic day count)
- Each has its own config activity and settings
- Settings stored per-widget-instance using SharedPreferences
- Widget renders as bitmap on Canvas, not using RecyclerView or other view recycling
- Weekly widget column weights: Today (2.0x), Past (0.6x), Future (1.5x decaying to 0.7x)
- Daily widget: Number of days equals widget width in cells (1-7 days), auto-calculated from widget size
- Daily widget start day: Always starts with current day, or next day if all events for today have elapsed

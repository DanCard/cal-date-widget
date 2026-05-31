# Fix Widget Freshness Across Day and Clock Changes

## Summary
- Refresh all widget types when the effective day or device clock context changes.
- Keep the existing periodic `updatePeriodMillis` fallback and click-triggered delayed refresh.
- Do not change daily widget auto-advance behavior; only fix stale refresh timing.

## Implementation Changes
- Add a shared coordinator that recognizes `ACTION_DATE_CHANGED`, `ACTION_TIME_CHANGED`, and `ACTION_TIMEZONE_CHANGED`.
- Use that coordinator from `DateWidgetProvider`, `DailyWidgetProvider`, and `WeeklyWidgetProvider` so each provider refreshes all of its active widget instances on those broadcasts.
- Register those broadcast actions for all three widget receivers in `AndroidManifest.xml`.
- Keep daily and weekly refreshes asynchronous with their existing `goAsync` coroutine pattern.

## Tests
- Add unit tests for the shared coordinator action filter and widget-id enumeration.
- Add a provider integration test that proves a time-change broadcast causes a widget refresh using updated saved settings.
- Run `./gradlew testDebugUnitTest`.

## Assumptions
- The stale “yesterday” state is caused by inexact widget scheduling rather than bad calendar date math.
- Full clock sync is desired, including manual emulator time changes and timezone changes.
- The daily widget’s auto-advance to tomorrow after all today events end is intentional and remains unchanged.

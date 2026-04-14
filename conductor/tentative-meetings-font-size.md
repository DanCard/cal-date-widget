# Tentative Meetings Font Size Adjustment

## Objective
Update the widget's logic to treat "Tentative" (Maybe) meeting responses as "less interesting," effectively reducing their font size to match meetings with "Invited" (no response yet) and "Declined" statuses. Additionally, ensure that event deduplication prioritizes "Accepted" meetings over "Tentative" ones.

## Key Files & Context
- `app/src/main/java/ai/dcar/caldatewidget/WidgetRenderingHelper.kt`: Contains the `isLessInteresting()` function which determines if an event gets the smaller font scaling.
- `app/src/main/java/ai/dcar/caldatewidget/WeeklyDisplayLogic.kt`: Contains the `filterNearDuplicates()` function which decides which instance of a duplicate event to display.
- `app/src/test/java/ai/dcar/caldatewidget/WidgetRenderingHelperTest.kt`: Unit tests for rendering rules.
- `app/src/test/java/ai/dcar/caldatewidget/WeeklyDisplayLogicTest.kt`: Unit tests for display logic and deduplication.

## Implementation Steps
1. **Update `WidgetRenderingHelper.kt`**
   - Modify the `isLessInteresting(event: CalendarEvent)` method to return `true` if `event.selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE` (value is 4).
   - The updated condition will look like: 
     `event.selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_INVITED || event.selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE || event.isDeclined`

2. **Update `WeeklyDisplayLogic.kt`**
   - In `filterNearDuplicates()`, update the `interestingPool` filter to exclude both `ATTENDEE_STATUS_INVITED` (3) and `ATTENDEE_STATUS_TENTATIVE` (4).
   - Change `it.selfStatus != 3` to `it.selfStatus != CalendarContract.Attendees.ATTENDEE_STATUS_INVITED && it.selfStatus != CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE`.

3. **Update Unit Tests**
   - In `WidgetRenderingHelperTest.kt`, add a test to verify that `isLessInteresting()` returns `true` for an event with `ATTENDEE_STATUS_TENTATIVE`.
   - In `WeeklyDisplayLogicTest.kt`, update or add a test to verify that `filterNearDuplicates()` prioritizes an accepted event over a tentative event.

## Verification & Testing
- Run all unit tests (`./scripts/unit-tests.sh`) to ensure the new logic for tentative events passes.
- Manually run the widget on the emulator (`./scripts/emulator-tests.sh` or direct deployment) to verify that tentative events are rendered with the smaller font size on both the daily and weekly widgets.

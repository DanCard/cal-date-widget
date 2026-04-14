# Fix Near-Duplicate Font Scaling Issue

## Background & Motivation
When the user has duplicate calendar events at the same time (e.g., from different calendars, some accepted/owned and some pending), the current `filterNearDuplicates` algorithm rotates through the duplicates blindly based on the time. If it selects an event that is pending or declined, the widget considers it "less interesting" and shrinks the text (small font). The user expects the widget to use the "biggest font rule" (i.e., not shrink the text) if *any* of the duplicate events are confirmed or self-created.

## Scope & Impact
- Updates `WeeklyDisplayLogic.filterNearDuplicates` (which is also used by the Daily widget).
- Updates unit tests in `WeeklyDisplayLogicTest.kt`.
- Impact is isolated to the event de-duplication selection algorithm. It ensures the "most interesting" version of a duplicate event is displayed.

## Proposed Solution
Inside `filterNearDuplicates()`, before applying the time-based rotation, filter the cluster of duplicate events to prefer "interesting" events. 
An event is considered "less interesting" if it is declined (`isDeclined == true`) or pending (`selfStatus == 3`, which corresponds to `CalendarContract.Attendees.ATTENDEE_STATUS_INVITED`).

```kotlin
// Select one event from each cluster
val filtered = clusters.map { cluster ->
    if (cluster.size == 1) {
        cluster[0]
    } else {
        // Prioritize events that are NOT "less interesting" (i.e. not pending/invited and not declined)
        // 3 = CalendarContract.Attendees.ATTENDEE_STATUS_INVITED
        val interestingPool = cluster.filter { it.selfStatus != 3 && !it.isDeclined }
        val pool = if (interestingPool.isNotEmpty()) interestingPool else cluster

        // Time-based rotation: changes roughly every minute
        val seed = (currentTimeMillis / 60000 + pool.sumOf { it.id }) % pool.size
        val selected = pool[seed.toInt()]
        android.util.Log.d("NearDuplicate", "Selected '\${selected.title}' from pool of \${pool.size} (cluster of \${cluster.size})")
        selected
    }
}
```

## Implementation Steps
1. Modify `app/src/main/java/ai/dcar/caldatewidget/WeeklyDisplayLogic.kt` as described above.
2. Modify `app/src/test/java/ai/dcar/caldatewidget/WeeklyDisplayLogicTest.kt` to include a test verifying that an interesting event is preferred over a pending one. 
   - Add a test case: `filterNearDuplicates prioritizes interesting events over pending ones`

## Verification
- Run `run-unit-tests.sh` (or specifically `./gradlew testDebugUnitTest --tests ai.dcar.caldatewidget.WeeklyDisplayLogicTest`).
- Check that all tests pass.

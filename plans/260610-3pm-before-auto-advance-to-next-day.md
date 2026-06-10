# Gate daily auto-advance behind the cutoff hour (default 3 PM)

## Context

The Daily widget auto-advances to "tomorrow" once today is effectively over. Today the
rule is asymmetric (`DailyDisplayLogic.shouldAutoAdvance`):

- **If today has non-declined events:** advances the instant the last one ends — *no
  time-of-day gate*. A 9:30 AM meeting that is your only event flips the widget to
  tomorrow at 9:30 AM.
- **If today has no events:** advances only after a configurable cutoff that already
  defaults to 3 PM (`PrefsManager.DEFAULT_DAILY_AUTO_ADVANCE_CUTOFF_MINUTE_OF_DAY = 15*60`).

The user wants today's view to persist until the cutoff **in all cases** — i.e., never
jump to tomorrow before 3 PM, even when every event has already ended. The cutoff stays
user-configurable (the existing setting and config UI are reused; the default 3 PM is
unchanged).

## Approach

The cutoff becomes a hard precondition for advancing; the "all events ended" check is
applied *in addition*, not as an early-exit override.

### 1. `DailyDisplayLogic.shouldAutoAdvance` (`app/src/main/java/ai/dcar/caldatewidget/DailyDisplayLogic.kt:64`)

Reorder so the cutoff is checked first and gates everything:

```kotlin
fun shouldAutoAdvance(
    todayEvents: List<CalendarEvent>,
    nowMillis: Long,
    cutoffMinuteOfDay: Int = PrefsManager.DEFAULT_DAILY_AUTO_ADVANCE_CUTOFF_MINUTE_OF_DAY
): Boolean {
    val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    if (minuteOfDay(calendar) < cutoffMinuteOfDay.coerceIn(0, (24 * 60) - 1)) {
        return false   // never advance before the cutoff
    }

    val validEvents = todayEvents.filter { !it.isDeclined }
    // After the cutoff: advance only once any remaining events have ended.
    return validEvents.all { it.endTime < nowMillis }
}
```

(`emptyList().all { ... }` is `true`, so the no-events case still advances at the cutoff —
no separate branch needed.)

Update the KDoc comment (lines 52–62) to describe the single combined rule: "Advances only
after the local cutoff hour AND once all non-declined events have ended."

### 2. `DailyDisplayLogic.getNextAutoAdvanceCheckTime` (`DailyDisplayLogic.kt:81`)

The scheduler must now also wake at the cutoff when events exist (previously it only
returned the next event end in that case, so it would never re-check at 3 PM). Make the
cutoff always a candidate and return the earliest future wake-up:

```kotlin
fun getNextAutoAdvanceCheckTime(...): Long? {
    if (shouldAutoAdvance(todayEvents, nowMillis, cutoffMinuteOfDay)) return null

    val validEvents = todayEvents.filter { !it.isDeclined }
    val nextEventEnd = validEvents
        .filter { it.endTime >= nowMillis }
        .minOfOrNull { it.endTime + AUTO_ADVANCE_EVENT_END_BUFFER_MILLIS }

    val cutoffTime = /* today's cutoff millis, as in current lines 97-104 */
    val cutoffCandidate = if (nowMillis < cutoffTime) cutoffTime else null

    return listOfNotNull(nextEventEnd, cutoffCandidate).minOrNull()
}
```

Waking at an intermediate event end before the cutoff is harmless: `shouldAutoAdvance`
returns `false` (cutoff not reached), the widget re-renders to refresh past-event dimming,
and re-schedules toward the next milestone. The chain terminates when both conditions hold.

### 3. Realign tests (`app/src/test/java/ai/dcar/caldatewidget/AutoAdvanceTest.kt`)

These currently assert the old "advance before cutoff" behavior and must flip:

- `returns true when all events are in the past even before cutoff` (line 78) → now expect
  **false** at 14:59; rename accordingly.
- `returns true at 1pm when last event ended at noon` (line 90) → now expect **false** (1 PM
  is before the 18:30 cutoff used there); rename to reflect that the cutoff gates it.
- `getNextAutoAdvanceCheckTime returns null after auto advance condition is met` (line 180) →
  now (13:00, event ended noon, default 3 PM cutoff) should return the **3 PM cutoff**, not
  null.

These stay valid as-is (good regression coverage): empty-day cutoff tests (39–65), future
event after cutoff (67–76), all-past-after-cutoff (97–107), declined-event tests (109–141),
`getNextAutoAdvanceCheckTime` next-event-end (143–156, min picks 12:30 < 3 PM), empty-day
cutoff scheduler tests (158–178).

Add new tests for the now-distinct case: **all events ended but before cutoff** →
`shouldAutoAdvance` false AND `getNextAutoAdvanceCheckTime` returns the cutoff time.

### Not changing

- `PrefsManager` (setting + 3 PM default already exist).
- `DailyConfigActivity` (cutoff time-picker UI already wired — keeps it configurable).
- `DailyAutoAdvanceScheduler` (already passes `settings.dailyAutoAdvanceCutoffMinuteOfDay`
  through; benefits automatically from the corrected check time).

## Verification

1. `./gradlew testDebugUnitTest` — all `AutoAdvanceTest` cases green, including the new
   before-cutoff/all-ended case.
2. On device (SM-F936U1): with a calendar whose only event ends in the morning, confirm the
   Daily widget keeps showing **today** until 3 PM rather than flipping early. Adjust the
   cutoff via the Daily widget config time-picker and confirm the gate moves with it.
3. Watch scheduling: `adb logcat -s DailyAutoAdvance:D` — verify `nextCheckTime` lands on the
   cutoff (not the last event end) when all events have already ended before the cutoff.

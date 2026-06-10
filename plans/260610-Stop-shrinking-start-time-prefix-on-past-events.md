# Stop shrinking the start-time prefix on past events

## Context

Past events on today's column now render at a tiny, capped size (earlier change). On top of
that size reduction, there is *separate* logic that shrinks the **start-time prefix** to 50%
of the title for past events:

- `WidgetRenderingHelper.decideEventLayout` (`WidgetRenderingHelper.kt:86`):
  `timeScale = if (useTimeScaleForPast && isPastTodayEvent) 0.5f else 1f`
- Applied to only the time span in `buildEventText` via `RelativeSizeSpan(timeScale)`
  (`WidgetRenderingHelper.kt:145-152`).
- Enabled at both render call-sites: Daily (`CalendarImageGenerator.kt:235`) and Weekly
  (`CalendarImageGenerator.kt:110`), each `useTimeScaleForPast = true`.

The two reductions compound, making the time on a past event nearly illegible (confirmed via
device screenshot — the "12:00" on the past "Garbage out?" event is half the title size while
active events show proportional times). Desired: for past events, the time prefix should be the
**same size as the event text**.

## Approach

Flip the existing, already-plumbed flag off at both call-sites in
`app/src/main/java/ai/dcar/caldatewidget/CalendarImageGenerator.kt`:

- Line 110 (Weekly `ColumnRenderConfig`): `useTimeScaleForPast = false`
- Line 235 (Daily `ColumnRenderConfig`): `useTimeScaleForPast = false`

This sets `timeScale = 1f` for past events, so the time prefix matches the title size. The
mechanism (the flag, the `decideEventLayout` branch, the `RelativeSizeSpan` path) stays intact
and reversible — it is simply disabled.

No height/clipping risk: the time prefix shares a line with the title, and the title is the
taller span, so line height is unaffected by enlarging the (still ≤ title) time text.

### Tests

No required changes. The only test references (`CalendarImageGeneratorRefactoredTest.kt:111,122`)
construct their own `ColumnRenderConfig` and assert only `pastEventScaleFactor`, so they are
unaffected. Optionally add a focused assertion that `decideEventLayout(..., useTimeScaleForPast
= false)` returns `timeScale == 1f` for a past event, to lock in the intent.

## Verification

1. `./gradlew testDebugUnitTest` — full suite still green.
2. `ANDROID_SERIAL=RFCT71FR9NT ./gradlew installDebug`, then tap / re-add the Daily widget.
3. Screenshot the Fold4 (`adb -s RFCT71FR9NT shell screencap` + pull) and confirm the time on a
   past event now matches the title text size, while active events are unchanged.

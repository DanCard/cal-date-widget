# Make past events render at a tiny, room-independent font size

## Context

On today's column, events that have already ended (`isPastTodayEvent`) are sized
*relative to the column's optimal scale*: `optimalScale * pastEventScaleFactor`
(`WidgetRenderingHelper.decideEventLayout`, `WidgetRenderingHelper.kt:53`). The optimal
scale comes from a binary search (0.7–1.5) that grows text to fill available height
(`calculateOptimalFontScale`). The consequence: on a sparse day the optimal scale climbs to
~1.5, so a past event renders at `1.5 × 0.8 = 1.2` — **larger** than normal text. "Plenty of
room" inflates past events, the opposite of what's wanted. A `0.7` floor also makes "tiny"
unreachable today.

Desired: past-today events should be a **tiny, fixed ~0.55× size, used as a ceiling** — never
larger regardless of available room, but allowed to shrink further in extreme cramping. Because
`decideEventLayout` is shared by both the measurement pass and the render pass, shrinking the
past automatically frees vertical space and lets future events grow.

This applies to both widgets (shared renderer): Daily (`pastEventScaleFactor = 0.8`,
`CalendarImageGenerator.kt:233`) and Weekly (`= 0.7`, `:108`).

## Approach

All changes are in `app/src/main/java/ai/dcar/caldatewidget/WidgetRenderingHelper.kt`.

### 1. Add constants (near `BASE_TEXT_SIZE`, line 38)

```kotlin
const val PAST_EVENT_MAX_SCALE = 0.55f   // ceiling for ended events on today's column
const val PAST_EVENT_MIN_SCALE = 0.40f   // floor so the cap isn't overridden
```

### 2. Cap + re-floor past events in `decideEventLayout` (lines 63–72)

Make the past branch a **cap** (`minOf`) instead of a plain multiply, and give past events
their own lower floor so the cap actually takes effect (today's `0.7` floor would otherwise
clamp it back up):

```kotlin
var scale = when {
    isPastTodayEvent -> minOf(optimalScale * pastEventScaleFactor, PAST_EVENT_MAX_SCALE)
    isLessInteresting -> {
        val invScale = (0.8f - 0.2f * optimalScale).coerceIn(0.5f, 0.7f)
        optimalScale * invScale
    }
    else -> optimalScale
}
val minScale = when {
    isPastTodayEvent -> PAST_EVENT_MIN_SCALE
    isLessInteresting -> 0.5f
    else -> 0.7f
}
if (scale < minScale) scale = minScale
```

Behavior: roomy/normal days → `optimalScale * factor` (≥0.56) is capped to **0.55** (tiny,
fixed). The cap can drop below 0.55 only if `optimalScale * factor` ever computes lower — the
"cap only, may shrink further" semantics. `forceOneLine` and `timeScale = 0.5` for past events
(lines 74–76) stay unchanged.

`pastEventScaleFactor` remains the per-widget knob; with the new cap it now governs only the
(rare) shrink-below-cap path, while 0.55 governs the common case. No call-site changes needed
in `CalendarImageGenerator.kt`.

### 3. Tests (`app/src/test/java/ai/dcar/caldatewidget/WidgetRenderingHelperTest.kt`)

Add direct `decideEventLayout` coverage (none exists today):
- **Roomy day cap:** with a high `optimalScale` (e.g. 1.5) and `isPastTodayEvent = true`,
  assert `eventScale == PAST_EVENT_MAX_SCALE` (0.55) — proves room no longer inflates past
  events. Contrast with `isPastTodayEvent = false` returning ~1.5.
- **Floor:** assert a past event never falls below `PAST_EVENT_MIN_SCALE`.
- **Non-past unaffected:** normal/declined branches still return their existing values
  (regression guard for the reordered `minScale` `when`).

The existing `measureTotalHeightForScale` / `calculateOptimalFontScale` tests (lines 45–190)
should still pass; re-run them since past-event height now feeds the search differently.

## Verification

1. `./gradlew testDebugUnitTest` — new `decideEventLayout` cap/floor tests green, existing
   helper + overlap tests still pass.
2. Build & install to the Fold4: `ANDROID_SERIAL=RFCT71FR9NT ./gradlew installDebug`, then
   remove/re-add (or tap) the Daily widget to force a fresh render.
3. Visual check on a **sparse** today with at least one ended event earlier in the day:
   confirm the past event is now tiny (~26px) rather than inflated, and that the remaining
   future event(s) grew to use the freed space.
4. `adb -s RFCT71FR9NT logcat -s CalendarImageGenerator:D` to confirm rendering and inspect
   scale logging if needed.

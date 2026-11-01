# Session Summary: Widget Bitmap Drawn at Wrong Orientation's Size (2026-11-01)

## Bug Report

On `emulator-5554` (Android 16 tablet, 2560x1600 at 320dpi, landscape), the weekly widget had
lots of blank space on the right and its columns were too narrow. The bottom was also cut off
(the last word of "Empty small trash cans").

## Root Cause

The launcher reports one widget size per screen layout in `appWidgetSizes`.
`WidgetColumnRenderer.calculateWidgetSize` always used `sizes[0]`, which is portrait on this
launcher. The layout uses `scaleType="matrix"`, so the bitmap is drawn 1:1 from the top-left
without scaling.

| | Width | Height |
|---|---|---|
| Space on screen (landscape) | 1344px (672dp) | 466px (233dp) |
| Bitmap drawn before the fix | 912px (456dp) | 618px (309dp) |
| **Result** | 432px blank on the right, columns squeezed | 152px cut off at the bottom |

## Fix

The app now gives the launcher one bitmap per reported size using
`RemoteViews(Map<SizeF, RemoteViews>)` (Android 12+), and the launcher picks the right one.
Choosing a single size in the app wouldn't be enough, because rotating or folding doesn't
reliably wake the provider, so the old bitmap would stay on screen until the next refresh.

| File | Change |
|------|--------|
| `WidgetUpdateHelper.kt` | New `pushViews()` sends one view per size; view building split into `buildWeeklyViews()` / `buildDailyViews()` |
| `WidgetColumnRenderer.kt` | New `reportedSizesDp()`, `selectSizeForScreen()`, `toWidgetSize()`; `calculateWidgetSize()` picks the current layout's size |
| `WeeklyWidgetRenderer.kt`, `DailyWidgetRenderer.kt` | `draw()` takes the size as a parameter |
| `CalendarImageGenerator.kt` | Passes the size through |

Related behavior:

- **Fallback if that's too much memory:** Android limits a widget's total bitmap memory. If the
  per-size update throws, `pushViews()` logs a warning and sends a single bitmap for the current
  layout, chosen by `selectSizeForScreen()`.
- **Android 8-11:** these versions had the same portrait assumption. In landscape they now use
  max width and min height.
- **Daily widget:** it shares this code, so it gets the same fix.

`draw()` has no side effects (auto-advance is decided per render and nothing is persisted), so
rendering once per size is safe.

## Testing

- New `WidgetSizeSelectionTest.kt` with 6 tests. They cover both orientations regardless of list
  order, a foldable cover screen, no size fitting the screen, and duplicate, empty and missing
  sizes.
- Full suite: 210 tests pass (`./gradlew testDebugUnitTest`).
- Installed the debug APK on `emulator-5554` only. Screenshots confirmed the widget fills its
  whole width with nothing cut off, in both landscape and portrait.
- After rotating to portrait, the correct bitmap was on screen with no manual refresh.
- `views_bitmap_memory` went from 2,254,464 bytes (912x618x4) to 4,759,680 bytes
  ((912x618 + 1344x466)x4), confirming both bitmaps reached the launcher.

### Physical Devices

The install script put this build on the Pixel 7 Pro and Fold4 as well (`emulator-5556` had
disconnected).

- **Fold4, unfolded:** a screenshot showed all seven columns with nothing cut off. Its launcher
  reports one size per widget, so it uses the single-bitmap path and picks the same size as the old
  code. `hsResizeRatio=0.8333` in its options suggests the launcher scales the widget down, which
  would explain a 1739px bitmap fitting a frame of about 1520px.
- **Fold4 cover screen and Pixel 7 Pro:** Danny checked these by eye and reported they look good.
  The screens were off when screenshots were attempted, so no screenshots or measurements exist for
  them, and which code path the Pixel uses wasn't confirmed.
- **Daily widgets on the Fold4:** the log shows them drawing without errors.

### Not Tested

- **The fallback:** only its size selection has unit tests. No fallback warnings appeared on any of
  the three devices checked, so it has never run on a device.
- **Android 8-11 change:** not covered by a unit test or a device run.

## Notes / Gotchas

- Four devices were connected (two physical, two emulators). `./gradlew installDebug` would have
  installed on all of them, so the APK was installed with `adb -s emulator-5554 install -r`.
- The shell `APPWIDGET_UPDATE` broadcast was blocked again, but the reinstall triggered the widget
  refresh.
- `CalendarImageGenerator` logs size details only in the single-size path, so the per-size path
  logs no `reported=` line. `views_bitmap_memory` in `dumpsys appwidget` is the way to check it.
- Each refresh now draws and queries the calendar once per reported size, so about twice per
  update here. The time cost wasn't measured.

## Other Things Noticed (Not Changed)

- **Narrow Sun 1 and Mon 2:** this is the intended shrinking of empty columns, not this bug.
- **Empty Sun 1:** the Google Calendar widget lists events today, and the two struck-through ones
  look declined. Whether the calendar and declined-event settings explain the empty column wasn't
  checked.
- **Mid-word wraps:** "Garbag/e out?" still breaks mid-word in landscape, and "Daniel'/s" also does
  in portrait. This is a separate text-fitting issue.

## Status

The changes are **uncommitted** on `main`, and no release has been cut yet. The emulator, Pixel 7
Pro and Fold4 are running this build, and the emulator's rotation setting is restored.

## Lesson

`appWidgetSizes` is a list of possibilities, not the widget's current size. When the launcher
can show the widget at several sizes, let the host choose between them. The mismatch is obvious
on a tablet in landscape because the two sizes differ a lot. Why it wasn't noticed earlier on the
Fold4 wasn't investigated.

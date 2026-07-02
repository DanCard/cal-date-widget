# Closing the Single-Device-Testing Gap (2026-07-01)

Follow-up to `notes/260701-launch-readiness-assessment.md`, which accepted
"tested mainly on one device (Fold4)" as a launch gap. This plan lists how to
close it, cheapest first.

## Why the gap is unusually cheap to close here

The widgets render everything to a bitmap sized by launcher-reported
dimensions, so "device variance" collapses to a narrow input surface:

    widget px size × screen density × system font scale × launcher grid behavior

You don't need 30 phones — you need to sweep those inputs. (Apps using native
`RemoteViews` layouts inherit per-OEM view-inflation quirks that only real
devices reveal; a bitmap-rendering app is fully characterized by the numbers
fed to the renderer, which tests and emulators can fake perfectly.)

## Options, cheapest first

### 1. Play pre-launch report — free, automatic, zero effort ✅ do before launch
- Upload the `.aab` to the internal testing track (`fastlane internal`).
- Google automatically installs it on ~10 real physical devices (Pixels,
  Samsungs, various sizes/API levels), crawls it, and reports crashes +
  screenshots: Play Console → Testing → Pre-launch report.
- Read the report BEFORE promoting to production. Already on the launch path.
- Caveat: exercises the app/config activities well but may not place widgets
  on the home screen — pair with #2.
- Bonus: since `CalApp` delegates to the default crash handler and R8 runs
  with `-dontobfuscate`, any crash during Google's crawl shows up deobfuscated
  in the report.

### 2. Alternate launchers on the Fold4 — ~30 min, real hardware ✅ do before launch
- Install Nova Launcher, Microsoft Launcher, Lawnchair.
- Place all three widgets on each; resize through the extremes.
- Launchers differ mainly in widget cell sizing, `onAppWidgetOptionsChanged`
  values, and resize handles — exactly the inputs the renderer consumes.
- Tests the "other launcher" half of the gap with no extra hardware.

### 3. Emulator sweep — a couple of hours (font-scale check is the key part)
- SDK already present at `~/.Android/Sdk`; create AVDs spanning extremes:
  - Small phone (Pixel 4a-ish, 1080×2340)
  - Big phone (Pixel 8 Pro)
  - Tablet
  - On API 26 (minSdk floor — oldest date/text API behavior) and API 35.
- On each: place widgets, then crank Settings → Display → **font size** and
  **display size** to maximum. Accessibility font scale (up to 2.0×) is the
  classic widget-layout breaker and the #1 source of "text is cut off"
  one-star reviews — and text sizing is the heart of this app. One emulator at
  max font size finds most of what ten devices would.
- Can be scripted with `avdmanager` / `adb` (ask Claude to generate the sweep).

### 4. Parameterized Robolectric matrix tests — half a day, permanent guard (post-launch)
- The `CalendarImageGenerator` / renderer tests already draw bitmaps at fixed
  dimensions. Parameterize across realistic (width, height, density,
  fontScale) tuples, e.g.:
  - 2×2 daily cells at 320dp/160dp, hdpi vs xxxhdpi
  - fontScale 1.0 / 1.3 / 2.0
- Assert the invariants already tested: no text overlap, no clipping past
  bounds, headers present. `TextOverlapTest.kt` is the natural template.
- Converts "works on other devices" from a manual check into a regression
  guard that runs in every `./gradlew testDebugUnitTest`.

### 5. Firebase Test Lab — optional extra
- Free daily quota on real physical devices with screenshot capture.
- No code changes, no `INTERNET` permission needed (Google's infra, not the app).
- Only worth it if the pre-launch report leaves you wanting more device photos.

## Recommendation

| When | Do |
| :-- | :-- |
| Before launch | #1 pre-launch report + #2 alternate launchers (nearly free) |
| Before launch, if time | #3 emulator, at minimum the max-font-scale check |
| Post-launch hardening | #4 Robolectric matrix, guided by real vitals data on which configurations matter |
| Probably skip | #5 unless pre-launch report is insufficient |

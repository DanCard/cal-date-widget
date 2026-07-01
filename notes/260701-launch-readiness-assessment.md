# Play Store Launch Readiness Assessment (2026-07-01)

**Verdict: the app is considerably more launch-ready than feared.** Evidence-based
audit of the codebase, test suite, and store assets, plus the chosen launch
strategy (older personal account → straight to production, zero promotion,
deliberately understated listing copy).

---

## Why confidence should be higher

The things that actually cause "bad first experience" reviews are all handled:

- **166 unit tests across 24 files, zero failures** — full suite re-run green on
  2026-07-01. No `@Ignore`'d tests hiding problems.
- **The scary first-run paths don't crash:**
  - Permission denied → widget draws a friendly "tap to grant" prompt
    (`WidgetColumnRenderer.drawPermissionRequired()`).
  - No calendars / empty calendars → graceful empty list
    (`CalendarRepository` catches `SecurityException`, logs `Log.w`, returns empty).
  - Rendering exception → fallback error bitmap instead of a dead widget
    (`WeeklyWidgetRenderer` / `DailyWidgetRenderer` try-catch).
  - These three cover most one-star-review scenarios for a calendar widget.
- **Production bug visibility exists.** `CalApp` + `CrashStore` uncaught-exception
  handler persists crashes across restarts, surfaces them in the email bug report
  (`BugReportActivity`, reachable from MainActivity and every config screen), and
  delegates to the previous handler so Play Android vitals still records
  everything. `-dontobfuscate` + `-keepattributes SourceFile,LineNumberTable`
  keep traces readable. One crash → two independent visibility channels, no
  `INTERNET` permission needed.
- **Release plumbing is done.** Signed release config (`caldatewidget.jks` via
  `local.properties`), versionCode 2 / versionName "1.0", minSdk 26 /
  targetSdk 35, `minifyEnabled` + `shrinkResources`, sensible ProGuard keeps
  (providers, activities, WorkManager, `WidgetSettings`), adaptive icon with
  monochrome variant, `.aab` built June 25, fastlane lanes ready
  (`test`, `upload_metadata`, `internal`).
- Only `READ_CALENDAR` requested; exported components correctly scoped
  (widgets/launcher/configure exported, `WidgetClickReceiver` and
  `BugReportActivity` not); no `debuggable` flag.

## Corrections to the audit agent's report (don't worry about these)

1. **LICENSE is NOT a Play Store risk.** The agent flagged the custom
   "Source-Available (All Rights Reserved)" license as a policy problem — it
   isn't. Google reviews the app and store listing, not the GitHub repo's
   license; a proprietary app with public source is completely fine.
   (Separately: LICENSE had an uncommitted edit adding LLM-training permissions —
   commit or discard, orthogonal to launch.)
2. **ProGuard does NOT strip `Log.d` calls** without an `-assumenosideeffects`
   rule (the agent claimed it would). Harmless logcat noise in release; arguably
   useful for the email bug reports. Optional polish only.

## Store listing changes made (2026-07-01)

Rewrote fastlane copy in a deliberately understated tone (user's choice):

- `fastlane/metadata/android/en-US/short_description.txt` — now
  "Simple home screen widgets for your date and calendar events. Early release."
  (77 chars, under the 80 limit).
- `fastlane/metadata/android/en-US/full_description.txt` — dropped
  "sleek/premium/perfectly/incredibly professional" superlatives; leads with
  "small, early-release app… expect some rough edges"; plain feature list; new
  **Known Limitations** section (all verified true):
  - Developed/tested mainly on one device (Samsung Galaxy Z Fold4).
  - Widgets refresh periodically and on tap, not instantly on calendar change.
  - Interface is English-only (single `res/values/` dir confirmed).
  - Kept the privacy stance prominent — "no internet permission" is a factual
    claim, not hype, and the strongest differentiator.

Note: understated copy has an SEO cost — Play search weighs title/short-description
keywords, so discoverability drops. For a low-expectations launch that's a
feature. Changing it later is a 5-minute `fastlane upload_metadata`, no app
update required.

## Remaining launch steps (unlisted-ish path)

Account predates Nov 2023 → no closed-testing requirement; can go straight to
production and simply not promote.

1. **Host the privacy policy** at the URL configured in `strings.xml`
   (GitHub `PRIVACY_POLICY.md` link). The only true blocker — required because
   of `READ_CALENDAR`.
2. **Data safety form:** answer "No data collected" — correct since nothing
   leaves the device (Google defines collection as off-device transmission).
3. `fastlane upload_metadata` to push the understated copy, then
   `fastlane internal` → verify on the Fold4 → promote to production.
4. **Post-launch habit:** check Play Console → Quality → Android vitals daily
   for the first week. Zero promotion ≈ single-digit daily organic installs —
   statistically equivalent to a ~1% staged rollout, so a bad bug has a tiny
   blast radius: see it in vitals, fix, bump versionCode.

## Accepted gap (don't fix pre-launch)

Single-device testing means Pixel/OnePlus/other-launcher quirks will only
surface post-launch. The Known Limitations line + the in-app bug-report flow is
the right-sized mitigation at this stage.

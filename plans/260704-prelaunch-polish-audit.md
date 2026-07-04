# Pre-Launch Polish Audit (2026-07-04)

## Verdict: no blockers — could upload today

Verified current state:
- ✅ Privacy policy URL live (HTTP 200 at `strings.xml:26` GitHub link).
- ✅ `main` synced with `origin/main` (99e478e pushed).
- ✅ Release `.aab` rebuilt Jul 1 21:41 — postdates icon commit 39db8c9; carries new
  launcher icon. R8 mapping files present in `app/build/outputs/mapping/release/`.
- ✅ 166 tests green, nothing `@Ignore`'d.
- ✅ No secrets tracked in git (`caldatewidget.jks`, `local.properties`,
  fastlane API key all gitignored and untracked).
- ✅ Graceful degradation confirmed: permission denied → "tap to grant" render;
  empty/no calendars → empty list; render exception → error bitmap fallback.
- ✅ Manifest clean: only `READ_CALENDAR`, no INTERNET, exported flags correctly
  scoped, no debuggable flag.

## Worth fixing before upload

These cost an app update to change later (baked into the APK), unlike listing
copy which `fastlane upload_metadata` can swap anytime.

### 1. `date_widget_info.xml` is behind its siblings
File: `app/src/main/res/xml/date_widget_info.xml`
- `android:description="@string/app_name"` (line 3) → picker shows "Cal Date
  Widget" instead of describing the widget. Daily/weekly have real description
  strings (`@string/daily_widget_desc`, `@string/weekly_widget_desc`). Add a
  `date_widget_desc` string and reference it.
- Missing `targetCellWidth`/`targetCellHeight` (Android 12+ / API 31). Only has
  legacy `minWidth/minHeight` 40dp, so launchers estimate grid size with a
  ~70dp/cell heuristic that varies by OEM — one of the few "works on Fold4,
  weird elsewhere" risks left. Daily (2×2) and weekly (4×2) already have them.

### 2. Calendar event titles logged in release builds
- `CalendarRepository.kt:122-125,138` logs event titles via `Log.d`;
  `WidgetColumnRenderer.fetchAndFilterEvents` (lines ~297-315) logs counts/ids.
- ProGuard does NOT strip `Log` calls without an `-assumenosideeffects` rule
  (`-dontobfuscate` config is intentional and unrelated).
- Titles in logcat is mildly at odds with the "data never leaves the device"
  pitch. Tension: debug logs feed the email bug-report flow — but event titles
  in an emailed bug report is its own privacy leak, so that cuts both ways.
- Minimal fix: stop logging titles (log counts/IDs only).
  Fuller fix: release-only Log-stripping rule in `proguard-rules.pro`.

### 3. Confirm support email
`strings.xml:30` = `daniecarde55@gmail.com` for bug reports — differs from the
Play account email. Verify intentional (typo-adjacent). Needs user yes/no.

## Fine to defer to v1.1

- **Hardcoded user-facing strings** not in `strings.xml` (all English-only,
  disclosed in listing — hygiene, not breakage):
  - `WeeklyConfigActivity.kt:64` "Widget refreshing..."
  - `MainActivity.kt:55` "Pinned widgets are not supported on this launcher."
  - `BugReportActivity.kt:69,266,278` toasts/labels
  - ColorPickerDialog titles in `ConfigActivity.kt:137-154` and
    `BaseWidgetConfigActivity.kt:112-136` — these *duplicate existing*
    `strings.xml` resources (`label_text_color` etc.)
  - `BaseWidgetConfigActivity.kt:250` "$displayName [shared]" suffix
- **`allowBackup="true"`** (Manifest line 9): widget prefs keyed by
  `appWidgetId` can restore onto a device where those IDs mean nothing.
  Harmless (stale prefs sit unused) — low priority.
- **`previewLayout`** in all three widget info XMLs for a live Android 12+
  picker preview (currently `previewImage` only).
- **README refresh** — still leads with the date widget; doesn't reflect the
  3-widget reality.
- Optional `goAsync` timeout guard (work is bounded: one content query + one
  bitmap draw — low risk).

## Suggested execution order

1. Fix `date_widget_info.xml` (description string + target cells).
2. Remove event-title logging (or add release Log-strip rule).
3. Get user confirmation on `bug_report_email`.
4. `./gradlew testDebugUnitTest` → green.
5. `./gradlew bundleRelease` → fresh `.aab`.
6. Proceed with `plans/260701-playstore-launch-next-steps.md` steps 3-7
   (Console listing, declarations, internal testing, promote).

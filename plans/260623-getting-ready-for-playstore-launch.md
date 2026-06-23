# Play Store Launch — Bug Visibility & R8 Config

## Context

Goal: be able to tell whether real users are hitting bugs after launching on the
Play Store, **without** breaking the app's core promise. The app currently has
only `READ_CALENDAR`, **no `INTERNET` permission**, a privacy policy stating "no
third-party analytics/tracking" and a Data Safety answer of "No data collected,"
and it is **source-available on GitHub**. That offline + source-available stance
is a selling point, so the strategy is to gain bug visibility using channels that
require neither an internet permission nor a Data Safety change.

Two pillars:
1. **Google Play Android vitals** (free, automatic, no code, no policy change) —
   Google collects crash/ANR traces from opted-in users and deobfuscates them
   automatically as long as we upload an `.aab` (R8 `mapping.txt` rides along).
2. **Harden the existing on-device, user-initiated email bug report**
   (`BugReportActivity`) so it can attach the *actual* last crash even after the
   process restarted — today it only reads the volatile logcat buffer.

Decision already made by the user: keep `minifyEnabled true` (retain shrinking +
optimization + `shrinkResources`) but **disable obfuscation** via `-dontobfuscate`,
since the code is already public and unobfuscated traces help both vitals and the
on-device email reports. We are explicitly **not** adding Firebase/Sentry/Bugsnag.

## Changes

### 1. R8: keep minify, drop obfuscation
File: `app/proguard-rules.pro`
- Add `-dontobfuscate`.
- Add `-keepattributes SourceFile,LineNumberTable` so stack traces keep file +
  line numbers (R8 can strip these even without renaming).
- Leave existing `-keep` / `-dontwarn` rules untouched.
- `app/build.gradle` release block is unchanged (`minifyEnabled true`,
  `shrinkResources true` stay).

### 2. Persist uncaught crashes on-device (new Application class)
- New file `app/src/main/java/ai/dcar/caldatewidget/CalApp.kt`:
  - `class CalApp : Application()`.
  - In `onCreate`, capture the existing default handler, then
    `Thread.setDefaultUncaughtExceptionHandler { thread, throwable -> ... }`:
    write timestamp + thread name + `Log.getStackTraceString(throwable)` to a
    private file (e.g. `filesDir/last_crash.txt`, single-file overwrite), then
    delegate to the previously-installed handler so normal crash behavior +
    Android vitals reporting still happen.
  - Keep it tiny and dependency-free; writes only to app-private storage.
- Register it: in `app/src/main/AndroidManifest.xml`, add
  `android:name=".CalApp"` to the `<application>` tag (currently has no name).

### 3. Surface the persisted crash in the bug report
File: `app/src/main/java/ai/dcar/caldatewidget/BugReportActivity.kt`
- Add a `gatherLastCrash()` helper that reads `last_crash.txt` (returns a
  friendly "No crash recorded" when absent).
- Include it in `loadDiagnosticsAsync()` and render it in `updatePreviewText()`
  / `generateReportBody()` as a new `=== LAST RECORDED CRASH ===` section,
  gated by the existing app-logs switch (or a small dedicated switch if the
  layout allows — reuse `switchAppLogs` to avoid layout churn).
- Pattern mirrors existing `gatherAppLogs()` (try/catch returning a String).

### 4. Eliminate silent failure blind spots
File: `app/src/main/java/ai/dcar/caldatewidget/CalendarRepository.kt`
- Replace the three empty `catch (e: SecurityException) { }` blocks
  (around lines 129–131, 155–157, and ~190) with a `Log.w(TAG, "...", e)` so a
  revoked-permission / denied-read state is visible in logcat and the bug report
  instead of producing silently empty widgets.
- Optional same treatment for the empty-`catch` fallbacks the audit flagged in
  `BaseWidgetConfigActivity` (lower priority).

## Out of scope (intentionally)
- No Firebase/Sentry/Bugsnag, no `INTERNET` permission, no analytics — would force
  a Data Safety "Yes" and a privacy-policy rewrite for marginal gain over vitals.
- Non-bug launch logistics (host privacy policy URL, $25 dev account, Console
  draft, App Signing enrollment, screenshots) are tracked in
  `plans/260616-play_store_readiness.md` and not repeated here.

## Verification
- `./gradlew testDebugUnitTest` — existing suite stays green (no logic changes to
  display/repository beyond logging).
- `./gradlew bundleRelease` — confirm it still builds with `-dontobfuscate`; then
  inspect the `.aab` (or `app/build/outputs/mapping/release/mapping.txt`) to
  confirm a mapping file is still produced and bundled for vitals.
- Manual crash test on device: install release/debug build, force a deliberate
  uncaught exception (temporary throw or a known crash path), relaunch the app,
  open the bug report screen, and confirm the `=== LAST RECORDED CRASH ===`
  section shows the readable stack trace with file:line.
- Permission-revocation test: add a widget, revoke calendar permission in system
  settings, trigger a refresh, and confirm a `Log.w` from `CalendarRepository`
  appears in `adb logcat` (and would appear in the bug report logs).
- Post-launch: watch Play Console → Quality → Android vitals (Crashes & ANRs);
  confirm traces appear deobfuscated.

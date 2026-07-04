# Play Store Launch — Next Steps

## Context

Cal Date Widget is launch-ready per `notes/260701-launch-readiness-assessment.md`
(166 green tests, graceful failure paths, crash visibility via vitals + on-device
reports). Launch strategy already decided: production release with zero promotion
("unlisted-ish"), deliberately understated listing copy (rewritten & committed in
99e478e). User's Play account is an older personal account (no closed-testing
requirement), and an app draft already exists in Play Console — forms status
uncertain, user recalls "upload icons" was next.

Verified current state (2026-07-01):
- ✅ Privacy policy URL is **already live** (HTTP 200 at the GitHub link in
  `strings.xml:26`) — the former "only blocker" is done.
- ✅ Store graphics meet Play specs exactly: `fastlane/.../icon/1.png` 512×512,
  `featureGraphic/1.png` 1024×500, 3 phone screenshots 1080×1920.
- ⚠️ Local `main` is 1 commit ahead of `origin/main` (99e478e unpushed).
- ⚠️ Built `.aab` (Jun 25 22:33) is **stale** — predates the launcher-icon
  commit 39db8c9 by 11 minutes, so it ships the old icon. Must rebuild.
- ⚠️ No `fastlane/play-store-api-key.json` — user chose **manual Console upload**
  for the first release; fastlane API setup deferred.

## Steps

### 1. Sync the repo (Claude, ~1 min)
- `git add plans/260701-close-single-device-testing-gap.md`, commit.
- `git push origin main` — keeps the public source (LICENSE, descriptions)
  consistent with the live privacy-policy URL.

### 2. Rebuild the signed release bundle (Claude, ~2 min)
- `./gradlew bundleRelease` → fresh `app/build/outputs/bundle/release/app-release.aab`
  with the current launcher icon (versionCode 2 is fine — nothing uploaded yet).
- Sanity check: confirm the `.aab` timestamp is new and
  `app/build/outputs/mapping/release/` exists (mapping rides along for vitals).

### 3. Play Console — store listing (user, in browser, ~20 min)
Everything to paste/upload is already in the repo:
- **App name:** `fastlane/metadata/android/en-US/title.txt` ("Cal Date Widget")
- **Short description:** `short_description.txt` (77 chars, understated)
- **Full description:** `full_description.txt` (understated, Known Limitations included)
- **Graphics:** upload `icon/1.png`, `featureGraphic/1.png`, and the 3 files in
  `phoneScreenshots/` from `fastlane/metadata/android/en-US/images/`.

### 4. Play Console — required declarations (user, ~20 min)
Check the "Set up your app" dashboard checklist; complete whichever remain:
- **Privacy policy:** paste `https://github.com/DanCard/cal-date-widget/blob/main/PRIVACY_POLICY.md`
- **Data safety:** "Does your app collect or share required user data types?" → **No**
  (calendar data never leaves the device; no INTERNET permission).
- **Ads:** No ads.
- **App access:** All functionality available without special access/login.
- **Content rating questionnaire:** Utility category; no objectionable content → Everyone.
- **Target audience:** 13+ (avoids Families-policy overhead; a calendar widget
  isn't child-directed).
- **App category:** Tools or Productivity; add support email.

### 5. Internal testing upload (user, ~10 min)
- Release → Testing → Internal testing → Create release → drag in the fresh `.aab`.
- Opt in to **Play App Signing** when prompted (repo keystore becomes the upload key).
- Add own Google account as tester, install on the Fold4 via the opt-in link —
  verifies the *store-delivered, R8-minified* build end-to-end.

### 6. Pre-launch gate (user + Claude, ~1 hr elapsed)
- Read **Pre-launch report** (Testing → Pre-launch report) once it generates —
  Google runs the build on ~10 real devices; expect zero crashes.
- Do the 30-min alternate-launcher pass from
  `plans/260701-close-single-device-testing-gap.md` (#2): Nova / Microsoft
  Launcher / Lawnchair on the Fold4, place + resize all three widgets.

### 7. Promote to production (user, ~5 min)
- Promote the internal release to Production → submit for review (first personal
  reviews typically take 1–7 days).
- Per the chosen strategy: no promotion anywhere; organic installs only.

### 8. Post-launch watch (first week)
- Play Console → Quality → **Android vitals** daily; crashes arrive deobfuscated
  (`-dontobfuscate`). Fix → bump `versionCode` to 3 → re-upload.

## Verification
- Step 2: fresh `.aab` mtime > commit 39db8c9; `./gradlew testDebugUnitTest` green.
- Step 5: app installs from the internal-testing link on the Fold4; widgets render;
  new launcher icon shows.
- Step 6: pre-launch report shows no crashes/ANRs; launcher pass shows no layout breakage.
- Step 7: listing goes live with the understated copy; vitals dashboard populating.

## Division of labor
Steps 1–2 are things Claude can execute now. Steps 3–7 happen in the Play Console
browser UI under the user's Google account — Claude can't do them, but every
artifact they need (copy, graphics, `.aab`, form answers) will be staged and the
form answers are written above.

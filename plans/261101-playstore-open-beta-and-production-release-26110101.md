# Plan: Google Play Store Release 26110101 (Open Beta & Production Promotion)

Release version `26110101` to Google Play Store:
1. Update `versionCode` and `versionName` to `26110101` in `app/build.gradle` to match code and name.
2. Add Fastlane changelog `fastlane/metadata/android/en-US/changelogs/26110101.txt`.
3. Run test validation (`./scripts/unit-tests.sh`) to ensure everything compiles and passes cleanly.
4. Build signed release bundle and upload to **Open Beta** track via Fastlane (`~/.local/share/gem/ruby/3.3.0/bin/fastlane beta`).
5. Promote release `26110101` from Open Beta to **Production** track via Fastlane (`~/.local/share/gem/ruby/3.3.0/bin/fastlane promote_to_production`).
6. Verify release status against the Play API (track version codes) and report results.

## Included Changes
- `a779248` Fix widget bitmap drawn at the wrong orientation's size.

## Files Modified / Added
- `app/build.gradle`: Update `versionCode 26110101` and `versionName "26110101"`.
- `fastlane/metadata/android/en-US/changelogs/26110101.txt`: Add release notes for version 26110101.
- `plans/261101-playstore-open-beta-and-production-release-26110101.md`: Release plan file.

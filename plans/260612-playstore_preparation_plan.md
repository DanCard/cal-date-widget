# Google Play Store Release Preparation Plan

This guide outlines the remaining steps and configuration required to publish **Cal Date Widget** on the Google Play Store.

---

## 1. Project Configuration Checklist
We have already configured several key parts of the release configuration:
- [x] **Proguard/R8 Minification**: Enabled in [app/build.gradle](file:///home/dcar/projects/cal-date-widget/app/build.gradle) to shrink resources, optimize code, and obfuscate classes.
- [x] **Release Signing Architecture**: Modified `app/build.gradle` to load signing configurations dynamically from a local, un-tracked `keystore.properties` file.
- [x] **Git Protection**: Added keystore and credentials files (`*.jks`, `keystore.properties`) to [.gitignore](file:///home/dcar/projects/cal-date-widget/.gitignore).
- [x] **Fastlane Metadata**: Formatted [title.txt](file:///home/dcar/projects/cal-date-widget/fastlane/metadata/android/en-US/title.txt), updated [short_description.txt](file:///home/dcar/projects/cal-date-widget/fastlane/metadata/android/en-US/short_description.txt), and enriched [full_description.txt](file:///home/dcar/projects/cal-date-widget/fastlane/metadata/android/en-US/full_description.txt) to showcase all 3 widgets (Date, Daily, and Weekly).
- [x] **Fastlane Automation**: Created [fastlane/Appfile](file:///home/dcar/projects/cal-date-widget/fastlane/Appfile) and [fastlane/Fastfile](file:///home/dcar/projects/cal-date-widget/fastlane/Fastfile).

---

## 2. Generate Release Signing Key (Keystore)
To publish on the Play Store, you must sign the Android App Bundle (AAB). Generate your upload keystore file by running the following command in your terminal:

```bash
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
```

### Configure Local Signing:
1. Copy [keystore.properties.example](file:///home/dcar/projects/cal-date-widget/keystore.properties.example) to a new file named `keystore.properties` in the project root directory.
2. Edit `keystore.properties` and replace the placeholder credentials with your actual keystore path, alias, and passwords:
   ```properties
   storeFile=../my-release-key.jks
   storePassword=your_keystore_password
   keyAlias=your_key_alias
   keyPassword=your_key_password
   ```
   > [!IMPORTANT]
   > Do **NOT** commit `keystore.properties` or `my-release-key.jks` to Git. Keep them backed up securely.

---

## 3. Google Play Store Console Setup
To distribute the app, you need a developer account:

### Account & Fees
1. Go to the [Google Play Console](https://play.google.com/console/signup).
2. Register and pay the one-time **$25 USD** developer registration fee.
3. Complete identity verification (requires a government ID).

> [!WARNING]
> **New Tester Requirement (Important)**:
> For personal developer accounts created after **November 13, 2023**, Google requires a Closed Test with at least **20 testers** who must be opted-in for at least **14 days continuously** before you can apply for production access. 
> Plan to invite friends, family, or beta-test groups to meet this requirement.

---

## 4. Visual Store Assets
You will need to prepare the following visual assets for the Play Console Store Listing:

### 1. App Icon
- **Size**: 512 x 512 pixels.
- **Format**: 32-bit PNG.
- **File size limit**: Up to 1 MB.
- **Transparency**: Not allowed (must have a solid background).

### 2. Feature Graphic
- **Size**: 1024 x 500 pixels.
- **Format**: 24-bit PNG or JPEG.
- **Tip**: Keep important text/icons near the center to prevent cropping.

### 3. Screenshots (Phone, 7" Tablet, 10" Tablet)
- **Quantity**: Minimum of 2, maximum of 8 per device type.
- **Format**: PNG or JPEG (16:9 or 9:16 aspect ratio).
- **Capturing Widget Previews**:
  Launch an emulator or connect a device and capture screenshots using:
  ```bash
  # Take screenshot
  adb shell screencap -p /sdcard/screenshot.png
  # Pull to your computer
  adb pull /sdcard/screenshot.png ./fastlane/metadata/android/en-US/images/phoneScreenshots/
  ```

---

## 5. Privacy Policy Hosting
Google Play Store requires a publicly accessible URL for your privacy policy.
- The app points to `https://dcar.ai/cal-date-widget/privacy` in its configuration interface.
- Make sure to publish the markdown content from [PRIVACY_POLICY.md](file:///home/dcar/projects/cal-date-widget/PRIVACY_POLICY.md) to your website or a hosting service (like GitHub Pages, GitBook, or Notion) at that URL, or update the URL in [strings.xml](file:///home/dcar/projects/cal-date-widget/app/src/main/res/values/strings.xml) if you want to host it elsewhere.

---

## 6. How to Build & Deploy
Once you have configured `keystore.properties` locally, you can build and deliver the app.

### Manual AAB Build
Run this to compile the production-ready Android App Bundle:
```bash
./gradlew bundleRelease
```
The output file will be saved at:
`app/build/outputs/bundle/release/app-release.aab`

### Automation with Fastlane
If you install Fastlane (`gem install fastlane` or `brew install fastlane`), you can automate tasks:
```bash
# 1. Run all unit tests
fastlane test

# 2. Build the release bundle (.aab)
fastlane build

# 3. Upload descriptions, metadata and graphics to Google Play Store
fastlane upload_metadata

# 4. Build and push the binary to Google Play Internal Test track
fastlane internal
```
*Note: To use `fastlane internal` or `fastlane upload_metadata`, you must place your API credential JSON from Google Cloud Console at `fastlane/play-store-api-key.json`.*

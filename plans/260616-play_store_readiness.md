# Google Play Store Readiness Report

This report evaluates the current state of **Cal Date Widget** for a Google Play Store release, highlights pre-configured items, and outlines the remaining steps to successfully publish the app.

---

## 📊 Summary of Play Store Readiness

The project is **nearly 90% ready** from a codebase perspective. Below is a breakdown of the current readiness indicators:

| Check | Status | Details |
| :--- | :--- | :--- |
| **Release Build Compilation** | 🟢 **Ready** | `./gradlew bundleRelease` compiles successfully with no R8/minify errors. |
| **App Signing** | 🟢 **Ready** | Keystore (`caldatewidget.jks`) is configured in `local.properties`. |
| **Target SDK 35 (Android 15)** | 🟢 **Ready** | Complies with Google Play's latest requirements. |
| **Fastlane Integration** | 🟢 **Ready** | `Fastfile`, `Appfile`, and store metadata are fully set up. |
| **Store Metadata (en-US)** | 🟢 **Ready** | Short and full descriptions, titles, and screenshots are pre-defined. |
| **Privacy Policy** | 🟢 **Ready** | A comprehensive `PRIVACY_POLICY.md` is written. |
| **Permissions Audit** | 🟢 **Ready** | Only `READ_CALENDAR` is requested; properly handled at runtime. |

---

## 🛠️ Step-by-Step Release Checklist

Follow these steps to launch the app on Google Play:

### 1. Host your Privacy Policy (Critical)
Because the app requests the sensitive `READ_CALENDAR` permission, Google Play requires a live Privacy Policy URL.
* **Current Configured URL:** `https://github.com/DanCard/cal-date-widget/blob/main/PRIVACY_POLICY.md` (defined in `strings.xml`).
* **Action:** Host the contents of [PRIVACY_POLICY.md](file:///home/dcar/projects/cal-date-widget/PRIVACY_POLICY.md) at this URL. You can use GitHub Pages, a simple Web host, or a public Notion page.

### 2. Set Up a Google Play Developer Account
* Go to the [Google Play Console](https://play.google.com/console) and register.
* Pay the one-time $25 registration fee.

### 3. Create the Google Play Console App Draft
Google Play requires you to manually create the app draft in the console for the first upload:
1. Click **Create App** in the Console.
2. Enter the app name: **Cal Date Widget**.
3. Select **App** (not Game) and **Free**.
4. Complete the initial Declarations (e.g., Free app, Developer Program Policies).

### 4. Set Up API Access for Fastlane
To automate uploads using `fastlane upload_metadata` or `fastlane internal`:
1. In Play Console, go to **Setup** > **API Access**.
2. Create/Link a Google Cloud Project and create a **Service Account**.
3. Grant the Service Account **Release Manager** permissions for your app.
4. Download the Service Account key in JSON format.
5. Save the file to your project root as [fastlane/play-store-api-key.json](file:///home/dcar/projects/cal-date-widget/fastlane/play-store-api-key.json) (which is ignored by Git).

### 5. Enroll in Play App Signing (Recommended)
* When uploading your first release, opt-in to **Google Play App Signing**.
* Google will manage the main app signing key. The keystore in your repo (`caldatewidget.jks`) will act as your **Upload Key**.

---

## 💡 Recommended Enhancements

### 📱 Android 12+ Widget Previews (`previewLayout`)
By default, some widgets use static drawable preview images. Android 12+ launchers can render actual layout previews in the widget picker, which provides a more seamless and modern experience.

We should add `android:previewLayout` to your widget provider XML configurations. 

> [!TIP]
> This is a low-effort, high-impact polish that makes your app look incredibly professional in the launcher widget drawer.

#### Proposed XML Changes:

* **[date_widget_info.xml](file:///home/dcar/projects/cal-date-widget/app/src/main/res/xml/date_widget_info.xml)**:
```xml
<appwidget-provider ...
    android:previewImage="@drawable/preview_date_widget"
    android:previewLayout="@layout/widget_date"
    ... >
```

* **[weekly_widget_info.xml](file:///home/dcar/projects/cal-date-widget/app/src/main/res/xml/weekly_widget_info.xml)**:
```xml
<appwidget-provider ...
    android:previewImage="@drawable/preview_weekly_widget"
    android:previewLayout="@layout/widget_weekly"
    ... >
```

* **[daily_widget_info.xml](file:///home/dcar/projects/cal-date-widget/app/src/main/res/xml/daily_widget_info.xml)**:
```xml
<appwidget-provider ...
    android:previewLayout="@layout/widget_daily"
    ... >
```

---

## 📝 How to Fill Out Google Play Consoles Forms

When submitting the app, you will need to answer several declarations:

### 1. Data Safety Form
Since the app retrieves calendar events to render them on-device, you must declare how data is handled:
* **Does your app collect or share any of the required user data types?** -> **No**.
  *(Google defines "collection" as transmitting data off the device. Since your app processes calendar data strictly on-device and has no internet permission, no collection occurs).*

### 2. Permissions Declaration
* You may be asked to explain why the app requires `READ_CALENDAR`.
* **Explanation:** *"The application is a calendar and date widget. It requires calendar access to fetch and render the user's scheduled calendar events on the home screen widgets."*

---

## 🚀 Deployment Commands

Once your service account key is placed in `fastlane/play-store-api-key.json`, you can run these Fastlane lanes:

```bash
# Run tests and verify build locally
fastlane test

# Upload metadata, descriptions, and screenshots to Play Store draft
fastlane upload_metadata

# Build and upload the release bundle to the Google Play Internal Test track
fastlane internal
```

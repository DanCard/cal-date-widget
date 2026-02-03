# Plan: Replace Feature Tour Screenshots with Text Art Mockups

## Summary
Replace the 6 PNG/GIF images in the feature tour with styled text art using Unicode box-drawing characters. Include an animated text art mockup for the API toggle section.

## Benefits
- Resolution-independent (no scaling artifacts)
- Consistent visual style across all sections
- Easier to maintain (edit text vs re-capture screenshots)
- Smaller APK size
- Unique, polished aesthetic

## Implementation

### 1. Create Text Art String Resources
**File:** `app/src/main/res/values/strings.xml`

Add new string resources for each mockup:

```xml
<!-- Widget Sizes Mockup -->
<string name="tour_mockup_widget_sizes" translatable="false">
┌─────┐  ┌─────────────────┐  ┌─────────────────┐
│ 72° │  │ ◀ Yest  Today ▶ │  │ 72°        Meteo│
│     │  │   68°   72°     │  │ ◀ ▓▓░░░  ▓▓░░ ▶│
└─────┘  └─────────────────┘  └─────────────────┘
  1x1           1x3                  2x3
</string>

<!-- Current Temperature Mockup -->
<string name="tour_mockup_current_temp" translatable="false">
┌────────────────────────┐
│ 72°              Meteo │
│                        │
│  Current temperature   │
│  updates automatically │
└────────────────────────┘
</string>

<!-- API Toggle State 1 (NWS) -->
<string name="tour_mockup_api_nws" translatable="false">
┌────────────────────────┐
│ 72°              [NWS] │  ← Tap to toggle
│    ▓▓░░░    ▓▓▓░░      │
└────────────────────────┘
</string>

<!-- API Toggle State 2 (Meteo) -->
<string name="tour_mockup_api_meteo" translatable="false">
┌────────────────────────┐
│ 72°            [Meteo] │  ← Tap to toggle
│    ▓▓░░░    ▓▓▓░░      │
└────────────────────────┘
</string>

<!-- Navigation Mockup -->
<string name="tour_mockup_navigation" translatable="false">
┌──────────────────────────────┐
│ 72°                    Meteo │
│ ◀  Jan 28  Jan 29  Jan 30  ▶ │
│    ▓▓░░    ▓▓▓░░   ▓▓░░░     │
└──────────────────────────────┘
    ↑                        ↑
  30 days                 7 days
   back                  forecast
</string>

<!-- Accuracy Modes Mockup -->
<string name="tour_mockup_accuracy" translatable="false">
FORECAST_BAR:  ▓▓▓░░░ (yellow = predicted)
ACCURACY_DOT:  72° ●  (green ≤2° / yellow ≤5° / red >5°)
SIDE_BY_SIDE:  72° (N:68°)
DIFFERENCE:    72° (N:+4)
</string>

<!-- Settings Mockup -->
<string name="tour_mockup_settings" translatable="false">
┌─ Settings ─────────────────┐
│                            │
│  📍 Location: GPS / ZIP    │
│  🌡️ API: Alternate / NWS   │
│  📊 Accuracy: Forecast Bar │
│  📈 View Statistics        │
│                            │
└────────────────────────────┘
</string>
```

### 2. Create Mockup Text Style
**File:** `app/src/main/res/values/styles.xml`

```xml
<style name="TourMockupText">
    <item name="android:fontFamily">monospace</item>
    <item name="android:textSize">12sp</item>
    <item name="android:textColor">@color/widget_text_primary</item>
    <item name="android:background">@drawable/mockup_background</item>
    <item name="android:padding">16dp</item>
    <item name="android:lineSpacingExtra">2dp</item>
</style>
```

### 3. Create Mockup Background Drawable
**File:** `app/src/main/res/drawable/mockup_background.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#1A1A2E" />
    <corners android:radius="12dp" />
</shape>
```

### 4. Update Layout - Replace ImageViews with TextViews
**File:** `app/src/main/res/layout/activity_feature_tour.xml`

Replace each `<ImageView>` with:

```xml
<TextView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="@string/tour_mockup_widget_sizes"
    style="@style/TourMockupText"
    android:layout_marginBottom="24dp" />
```

For the API toggle section, use a ViewFlipper for animation:

```xml
<ViewFlipper
    android:id="@+id/api_toggle_flipper"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:autoStart="true"
    android:flipInterval="1500"
    android:inAnimation="@android:anim/fade_in"
    android:outAnimation="@android:anim/fade_out"
    android:layout_marginBottom="24dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/tour_mockup_api_nws"
        style="@style/TourMockupText" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/tour_mockup_api_meteo"
        style="@style/TourMockupText" />

</ViewFlipper>
```

### 5. Update Activity Code
**File:** `app/src/main/java/com/weatherwidget/ui/FeatureTourActivity.kt`

Remove Glide GIF loading code (no longer needed). The ViewFlipper handles animation automatically via XML attributes.

### 6. Delete Unused Image Assets
**Files to delete from `app/src/main/res/drawable-nodpi/`:**
- `tour_widget_sizes.png`
- `tour_current_temp.png`
- `tour_api_indicator.png`
- `tour_api_toggle.gif`
- `tour_navigation.png`
- `tour_accuracy_modes.png`
- `tour_settings.png`

## Files to Modify
| File | Change |
|------|--------|
| `res/values/strings.xml` | Add 7 mockup strings |
| `res/values/styles.xml` | Add TourMockupText style |
| `res/drawable/mockup_background.xml` | New file for mockup background |
| `res/layout/activity_feature_tour.xml` | Replace ImageViews with TextViews/ViewFlipper |
| `ui/FeatureTourActivity.kt` | Remove Glide code |
| `res/drawable-nodpi/tour_*.png/.gif` | Delete 7 image files |

## Verification
1. Build and install: `./gradlew installDebug`
2. Open Settings → View Feature Tour
3. Verify:
   - All 6 text mockups render correctly with monospace font
   - Box-drawing characters display properly (no missing glyphs)
   - API toggle section animates between NWS/Meteo states
   - Text is readable at different screen sizes
   - Dark background provides good contrast
4. Compare APK size before/after (expect reduction)

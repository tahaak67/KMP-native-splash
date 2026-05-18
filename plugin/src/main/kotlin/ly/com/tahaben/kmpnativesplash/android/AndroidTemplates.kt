package ly.com.tahaben.kmpnativesplash.android

/** Skeleton for `launch_background.xml`. `<item>` children are appended programmatically. */
internal const val ANDROID_LAUNCH_BACKGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
</layer-list>
"""

internal fun launchItemBitmap(gravity: String, drawable: String): String =
    """    <item>
        <bitmap android:gravity="$gravity" android:src="@drawable/$drawable" />
    </item>
"""

internal fun brandingItemBitmap(gravity: String, bottomPaddingDp: Int, drawable: String): String =
    """    <item android:bottom="${bottomPaddingDp}dp">
        <bitmap android:gravity="$gravity" android:src="@drawable/$drawable" />
    </item>
"""

// styles.xml templates — base values/ folder
internal const val ANDROID_STYLES_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="LaunchTheme" parent="@android:style/Theme.Light.NoTitleBar">
        <item name="android:windowBackground">@drawable/launch_background</item>
    </style>
    <style name="NormalTheme" parent="@android:style/Theme.Light.NoTitleBar">
        <item name="android:windowBackground">?android:colorBackground</item>
    </style>
</resources>
"""

internal const val ANDROID_STYLES_NIGHT_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="LaunchTheme" parent="@android:style/Theme.Black.NoTitleBar">
        <item name="android:windowBackground">@drawable/launch_background</item>
    </style>
    <style name="NormalTheme" parent="@android:style/Theme.Black.NoTitleBar">
        <item name="android:windowBackground">?android:colorBackground</item>
    </style>
</resources>
"""

// values-v31/ — Android 12 SplashScreen API
internal const val ANDROID_V31_STYLES_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="LaunchTheme" parent="@android:style/Theme.Light.NoTitleBar">
        <item name="android:windowSplashScreenAnimatedIcon">@drawable/android12splash</item>
    </style>
    <style name="NormalTheme" parent="@android:style/Theme.Light.NoTitleBar">
        <item name="android:windowBackground">?android:colorBackground</item>
    </style>
</resources>
"""

internal const val ANDROID_V31_STYLES_NIGHT_XML = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="LaunchTheme" parent="@android:style/Theme.Black.NoTitleBar">
        <item name="android:windowSplashScreenAnimatedIcon">@drawable/android12splash</item>
    </style>
    <style name="NormalTheme" parent="@android:style/Theme.Black.NoTitleBar">
        <item name="android:windowBackground">?android:colorBackground</item>
    </style>
</resources>
"""

internal data class DpiBucket(val folder: String, val scale: Double)

internal val DPI_BUCKETS = listOf(
    DpiBucket("drawable-mdpi", 1.0),
    DpiBucket("drawable-hdpi", 1.5),
    DpiBucket("drawable-xhdpi", 2.0),
    DpiBucket("drawable-xxhdpi", 3.0),
    DpiBucket("drawable-xxxhdpi", 4.0),
)

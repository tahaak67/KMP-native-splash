package ly.com.tahaben.kmpsplash.ios

internal fun launchImageContentsJson(baseName: String): String = """{
  "images" : [
    { "idiom" : "universal", "filename" : "$baseName.png", "scale" : "1x" },
    { "idiom" : "universal", "filename" : "$baseName@2x.png", "scale" : "2x" },
    { "idiom" : "universal", "filename" : "$baseName@3x.png", "scale" : "3x" }
  ],
  "info" : { "version" : 1, "author" : "kmp-native-splash" }
}
"""

internal fun launchImageContentsJsonDark(baseName: String, darkName: String): String = """{
  "images" : [
    { "idiom" : "universal", "filename" : "$baseName.png", "scale" : "1x" },
    { "idiom" : "universal", "filename" : "$baseName@2x.png", "scale" : "2x" },
    { "idiom" : "universal", "filename" : "$baseName@3x.png", "scale" : "3x" },
    {
      "idiom" : "universal",
      "filename" : "$darkName.png",
      "scale" : "1x",
      "appearances" : [{"appearance" : "luminosity", "value" : "dark"}]
    },
    {
      "idiom" : "universal",
      "filename" : "$darkName@2x.png",
      "scale" : "2x",
      "appearances" : [{"appearance" : "luminosity", "value" : "dark"}]
    },
    {
      "idiom" : "universal",
      "filename" : "$darkName@3x.png",
      "scale" : "3x",
      "appearances" : [{"appearance" : "luminosity", "value" : "dark"}]
    }
  ],
  "info" : { "version" : 1, "author" : "kmp-native-splash" }
}
"""

internal const val LAUNCH_BACKGROUND_CONTENTS_JSON = """{
  "images" : [
    { "idiom" : "universal", "filename" : "background.png", "scale" : "1x" }
  ],
  "info" : { "version" : 1, "author" : "kmp-native-splash" }
}
"""

internal const val LAUNCH_BACKGROUND_CONTENTS_JSON_DARK = """{
  "images" : [
    { "idiom" : "universal", "filename" : "background.png", "scale" : "1x" },
    {
      "idiom" : "universal",
      "filename" : "darkbackground.png",
      "scale" : "1x",
      "appearances" : [{"appearance" : "luminosity", "value" : "dark"}]
    }
  ],
  "info" : { "version" : 1, "author" : "kmp-native-splash" }
}
"""

/**
 * Baseline LaunchScreen storyboard owned by this plugin. The well-known IDs (KSP-vw-001,
 * KSP-iv-001) let us re-patch it deterministically on subsequent runs.
 * Placeholders: {LAUNCH_IMAGE}, {LAUNCH_BACKGROUND}, {CONTENT_MODE},
 * {IMG_W}, {IMG_H}, {BG_W}, {BG_H}.
 */
internal const val LAUNCH_SCREEN_STORYBOARD = """<?xml version="1.0" encoding="UTF-8"?>
<document type="com.apple.InterfaceBuilder3.CocoaTouch.Storyboard.XIB" version="3.0" toolsVersion="22155" targetRuntime="iOS.CocoaTouch" propertyAccessControl="none" useAutolayout="YES" launchScreen="YES" useTraitCollections="YES" useSafeAreas="YES" colorMatched="YES" initialViewController="01J-lp-oVM">
    <device id="retina6_1" orientation="portrait" appearance="light"/>
    <dependencies>
        <plugIn identifier="com.apple.InterfaceBuilder.IBCocoaTouchPlugin" version="22131"/>
        <capability name="Safe area layout guides" minToolsVersion="9.0"/>
        <capability name="documents saved in the Xcode 8 format" minToolsVersion="8.0"/>
    </dependencies>
    <scenes>
        <scene sceneID="EHf-IW-A2E">
            <objects>
                <viewController id="01J-lp-oVM" sceneMemberID="viewController">
                    <view key="view" contentMode="scaleToFill" id="KSP-vw-001">
                        <rect key="frame" x="0.0" y="0.0" width="393" height="852"/>
                        <autoresizingMask key="autoresizingMask" widthSizable="YES" heightSizable="YES"/>
                        <subviews>
                            <imageView opaque="NO" clipsSubviews="YES" multipleTouchEnabled="YES" contentMode="scaleToFill" image="{LAUNCH_BACKGROUND}" translatesAutoresizingMaskIntoConstraints="NO" id="KSP-bg-001">
                                <rect key="frame" x="0.0" y="0.0" width="393" height="852"/>
                            </imageView>
                            <imageView opaque="NO" clipsSubviews="YES" multipleTouchEnabled="YES" contentMode="{CONTENT_MODE}" image="{LAUNCH_IMAGE}" translatesAutoresizingMaskIntoConstraints="NO" id="KSP-iv-001">
                                <rect key="frame" x="96.5" y="326" width="200" height="200"/>
                            </imageView>
                        </subviews>
                        <color key="backgroundColor" red="1" green="1" blue="1" alpha="1" colorSpace="custom" customColorSpace="sRGB"/>
                        <constraints>
                            <constraint firstItem="KSP-bg-001" firstAttribute="top" secondItem="KSP-vw-001" secondAttribute="top" id="KSP-bgt-001"/>
                            <constraint firstItem="KSP-bg-001" firstAttribute="leading" secondItem="KSP-vw-001" secondAttribute="leading" id="KSP-bgl-001"/>
                            <constraint firstItem="KSP-bg-001" firstAttribute="trailing" secondItem="KSP-vw-001" secondAttribute="trailing" id="KSP-bgr-001"/>
                            <constraint firstItem="KSP-bg-001" firstAttribute="bottom" secondItem="KSP-vw-001" secondAttribute="bottom" id="KSP-bgb-001"/>
                            <constraint firstItem="KSP-iv-001" firstAttribute="centerX" secondItem="KSP-vw-001" secondAttribute="centerX" id="KSP-cx-001"/>
                            <constraint firstItem="KSP-iv-001" firstAttribute="centerY" secondItem="KSP-vw-001" secondAttribute="centerY" id="KSP-cy-001"/>
                        </constraints>
                    </view>
                </viewController>
                <placeholder placeholderIdentifier="IBFirstResponder" id="ish-2x-Y9N" sceneMemberID="firstResponder"/>
            </objects>
            <point key="canvasLocation" x="53" y="375"/>
        </scene>
    </scenes>
    <resources>
        <image name="{LAUNCH_IMAGE}" width="{IMG_W}" height="{IMG_H}"/>
        <image name="{LAUNCH_BACKGROUND}" width="{BG_W}" height="{BG_H}"/>
    </resources>
</document>
"""

internal fun brandingImageViewXml(brandingImageName: String, mode: ly.com.tahaben.kmpsplash.dsl.BrandingMode): String {
    val rect = when (mode) {
        ly.com.tahaben.kmpsplash.dsl.BrandingMode.BOTTOM -> """<rect key="frame" x="146.5" y="700" width="100" height="100"/>"""
        ly.com.tahaben.kmpsplash.dsl.BrandingMode.BOTTOM_LEFT -> """<rect key="frame" x="16" y="700" width="100" height="100"/>"""
        ly.com.tahaben.kmpsplash.dsl.BrandingMode.BOTTOM_RIGHT -> """<rect key="frame" x="277" y="700" width="100" height="100"/>"""
    }
    return """<imageView opaque="NO" clipsSubviews="YES" multipleTouchEnabled="YES" contentMode="scaleAspectFit" image="$brandingImageName" translatesAutoresizingMaskIntoConstraints="NO" id="KSP-br-001">
                                $rect
                            </imageView>"""
}

internal fun brandingConstraintsXml(mode: ly.com.tahaben.kmpsplash.dsl.BrandingMode, bottomPadding: Int): String {
    val padding = bottomPadding.coerceAtLeast(0)
    val (horizontalAttr1, horizontalAttr2, horizontalId) = when (mode) {
        ly.com.tahaben.kmpsplash.dsl.BrandingMode.BOTTOM -> Triple("centerX", "centerX", "KSP-bcx-001")
        ly.com.tahaben.kmpsplash.dsl.BrandingMode.BOTTOM_LEFT -> Triple("leading", "leading", "KSP-blg-001")
        ly.com.tahaben.kmpsplash.dsl.BrandingMode.BOTTOM_RIGHT -> Triple("trailing", "trailing", "KSP-btr-001")
    }
    val constant = if (mode == ly.com.tahaben.kmpsplash.dsl.BrandingMode.BOTTOM_RIGHT) "-16" else "16"
    val horizontalConstant =
        if (mode == ly.com.tahaben.kmpsplash.dsl.BrandingMode.BOTTOM) "" else """ constant="$constant""""
    return """<constraint firstItem="KSP-br-001" firstAttribute="$horizontalAttr1" secondItem="KSP-vw-001" secondAttribute="$horizontalAttr2"$horizontalConstant id="$horizontalId"/>
                            <constraint firstItem="KSP-vw-001" firstAttribute="bottom" secondItem="KSP-br-001" secondAttribute="bottom" constant="$padding" id="KSP-bbm-001"/>"""
}

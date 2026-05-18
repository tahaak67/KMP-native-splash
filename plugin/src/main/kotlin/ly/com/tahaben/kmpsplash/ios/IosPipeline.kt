package ly.com.tahaben.kmpsplash.ios

import ly.com.tahaben.kmpsplash.ResolvedBranding
import ly.com.tahaben.kmpsplash.ResolvedSplashSpec
import java.io.File

internal class IosWriteReport(
    val imageSets: MutableList<File> = mutableListOf(),
    var storyboard: File? = null,
    val patchedPlists: MutableList<File> = mutableListOf(),
    val skippedPlists: MutableList<File> = mutableListOf(),
)

/**
 * Writes the iOS launch assets — imagesets in Assets.xcassets, the storyboard, and
 * patches every configured Info.plist — using [iosAppDir] as the base.
 */
internal fun writeIosSplash(spec: ResolvedSplashSpec, iosAppDir: File): IosWriteReport {
    val report = IosWriteReport()
    val suffix = if (spec.isDefaultVariant) "" else spec.variantName.replaceFirstChar { it.uppercaseChar() }
    val assets = File(iosAppDir, "Assets.xcassets")
    val baseLproj = File(iosAppDir, "Base.lproj")
    assets.mkdirs()
    baseLproj.mkdirs()

    val ios = spec.ios
    val launchImageDir = File(assets, "LaunchImage$suffix.imageset")
    val launchBackgroundDir = File(assets, "LaunchBackground$suffix.imageset")
    val brandingImageDir = File(assets, "BrandingImage$suffix.imageset")

    if (ios.image != null) {
        writeImageSet(
            src = ios.image,
            imagesetDir = launchImageDir,
            baseName = "LaunchImage",
            dark = ios.imageDark,
            darkBaseName = "LaunchImageDark",
        )
        report.imageSets += launchImageDir
    }

    writeBackgroundImageSet(
        imagesetDir = launchBackgroundDir,
        color = ios.color,
        backgroundImage = ios.backgroundImage,
        darkColor = ios.colorDark,
        backgroundImageDark = ios.backgroundImageDark,
    )
    report.imageSets += launchBackgroundDir

    val brandingResolved = pickIosBranding(spec.branding)
    if (brandingResolved != null) {
        writeImageSet(
            src = brandingResolved.image,
            imagesetDir = brandingImageDir,
            baseName = "BrandingImage",
            dark = brandingResolved.imageDark,
            darkBaseName = "BrandingImageDark",
        )
        report.imageSets += brandingImageDir
    }

    val storyboardName = "LaunchScreen$suffix"
    val storyboardFile = File(baseLproj, "$storyboardName.storyboard")
    writeStoryboard(
        StoryboardOptions(
            launchImageName = "LaunchImage$suffix",
            launchBackgroundName = "LaunchBackground$suffix",
            contentMode = ios.contentMode,
            foregroundImage = ios.image,
            brandingImageName = brandingResolved?.let { "BrandingImage$suffix" },
            brandingMode = spec.branding?.mode ?: ly.com.tahaben.kmpsplash.dsl.BrandingMode.BOTTOM,
            brandingBottomPadding = brandingResolved?.bottomPadding ?: 0,
        ),
        storyboardFile,
    )
    report.storyboard = storyboardFile

    ios.infoPlistFiles
        .map { File(iosAppDir, it) }
        .forEach { plist ->
            if (plist.exists()) {
                patchInfoPlist(plist, spec.fullscreen)
                report.patchedPlists += plist
            } else {
                report.skippedPlists += plist
            }
        }
    return report
}

private data class IosBranding(val image: File, val imageDark: File?, val bottomPadding: Int)

private fun pickIosBranding(branding: ResolvedBranding?): IosBranding? {
    if (branding == null) return null
    val img = branding.iosImage ?: branding.image ?: return null
    val imgDark = branding.iosImageDark ?: branding.imageDark
    val padding = branding.iosBottomPadding ?: branding.bottomPadding
    return IosBranding(img, imgDark, padding)
}

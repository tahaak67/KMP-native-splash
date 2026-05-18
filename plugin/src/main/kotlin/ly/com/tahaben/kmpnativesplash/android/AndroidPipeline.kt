package ly.com.tahaben.kmpnativesplash.android

import ly.com.tahaben.kmpnativesplash.ResolvedBranding
import ly.com.tahaben.kmpnativesplash.ResolvedSplashSpec
import ly.com.tahaben.kmpnativesplash.image.resize
import ly.com.tahaben.kmpnativesplash.image.solidColor
import ly.com.tahaben.kmpnativesplash.image.writePng
import ly.com.tahaben.kmpnativesplash.util.hexToRgb
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Writes the full Android res tree for the given [spec] under [outResDir]: foreground
 * splash, background, branding, Android 12 drawables, `launch_background.xml`, and
 * `styles.xml` — following KMP source-set conventions.
 */
internal fun writeAndroidSplash(spec: ResolvedSplashSpec, outResDir: File): AndroidWriteReport {
    outResDir.mkdirs()

    val report = AndroidWriteReport()
    val android = spec.android
    val hasDark = android.colorDark != null ||
            android.imageDark != null ||
            android.backgroundImageDark != null ||
            spec.branding?.imageDark != null ||
            spec.branding?.androidImageDark != null

    // ---- Foreground splash.png ----
    val foreground = android.image
    if (foreground != null) writePerDpi(foreground, outResDir, "splash.png", isDark = false, report)

    val foregroundDark = android.imageDark
    if (hasDark && foregroundDark != null) writePerDpi(foregroundDark, outResDir, "splash.png", isDark = true, report)

    // ---- Background (1×1 solid or copied image), goes to drawable/ and drawable-v21/ ----
    writeBackground(outResDir, "background.png", android.color, android.backgroundImage, isDark = false, report)
    if (hasDark) {
        val darkColor = android.colorDark ?: android.color
        writeBackground(
            outResDir,
            "background.png",
            darkColor,
            android.backgroundImageDark ?: android.backgroundImage,
            isDark = true,
            report
        )
    }

    // ---- Branding ----
    val brandingResolved = pickAndroidBranding(spec.branding)
    if (brandingResolved != null) {
        writePerDpi(brandingResolved.image, outResDir, "branding.png", isDark = false, report)
        brandingResolved.imageDark?.let { writePerDpi(it, outResDir, "branding.png", isDark = true, report) }
    }

    // ---- Android 12 (v31) drawables ----
    val a12 = spec.android12
    if (a12 != null) {
        val a12Image = a12.image ?: android.image
        if (a12Image != null) writePerDpiV31(a12Image, outResDir, "android12splash.png", isDark = false, report)
        val a12ImageDark = a12.imageDark ?: android.imageDark
        if (hasDark && a12ImageDark != null) writePerDpiV31(
            a12ImageDark,
            outResDir,
            "android12splash.png",
            isDark = true,
            report
        )

        val a12Branding = a12.brandingImage ?: brandingResolved?.image
        if (a12Branding != null) writePerDpiV31(a12Branding, outResDir, "android12branding.png", isDark = false, report)
        val a12BrandingDark = a12.brandingImageDark ?: brandingResolved?.imageDark
        if (hasDark && a12BrandingDark != null) writePerDpiV31(
            a12BrandingDark,
            outResDir,
            "android12branding.png",
            isDark = true,
            report
        )
    }

    // ---- launch_background.xml (drawable/, drawable-v21/, plus -night counterparts) ----
    val launchOpts = LaunchBackgroundOptions(
        hasForegroundImage = foreground != null,
        gravity = android.gravity,
        branding = brandingResolved?.let {
            LaunchBackgroundOptions.BrandingItem(
                mode = spec.branding!!.mode,
                bottomPaddingDp = it.bottomPadding,
            )
        },
    )
    val launchXml = renderLaunchBackgroundXml(launchOpts)
    File(outResDir, "drawable/launch_background.xml").write(launchXml)
    File(outResDir, "drawable-v21/launch_background.xml").write(launchXml)
    if (hasDark) {
        File(outResDir, "drawable-night/launch_background.xml").write(launchXml)
        File(outResDir, "drawable-night-v21/launch_background.xml").write(launchXml)
    }

    // ---- styles.xml ----
    val stylesOpts = StylesPatchOptions(
        fullscreen = spec.fullscreen,
        a12 = null,
    )
    renderStyles(ANDROID_STYLES_XML, stylesOpts, File(outResDir, "values/styles.xml"))
    if (hasDark) renderStyles(ANDROID_STYLES_NIGHT_XML, stylesOpts, File(outResDir, "values-night/styles.xml"))

    if (a12 != null) {
        val a12Opts = StylesPatchOptions(
            fullscreen = spec.fullscreen,
            a12 = StylesPatchOptions.A12Options(
                backgroundColor = a12.color?.let { hexAsAndroidString(it) },
                iconBackgroundColor = a12.iconBackgroundColor?.let { hexAsAndroidString(it) },
                hasAnimatedIcon = (a12.image ?: android.image) != null,
                hasBrandingImage = (a12.brandingImage ?: brandingResolved?.image) != null,
            ),
        )
        renderStyles(ANDROID_V31_STYLES_XML, a12Opts, File(outResDir, "values-v31/styles.xml"))
        if (hasDark) {
            val a12DarkOpts = StylesPatchOptions(
                fullscreen = spec.fullscreen,
                a12 = StylesPatchOptions.A12Options(
                    backgroundColor = (a12.colorDark ?: a12.color)?.let { hexAsAndroidString(it) },
                    iconBackgroundColor = (a12.iconBackgroundColorDark
                        ?: a12.iconBackgroundColor)?.let { hexAsAndroidString(it) },
                    hasAnimatedIcon = (a12.imageDark ?: a12.image ?: android.imageDark ?: android.image) != null,
                    hasBrandingImage = (a12.brandingImageDark ?: a12.brandingImage ?: brandingResolved?.imageDark
                    ?: brandingResolved?.image) != null,
                ),
            )
            renderStyles(ANDROID_V31_STYLES_NIGHT_XML, a12DarkOpts, File(outResDir, "values-night-v31/styles.xml"))
        }
    }

    return report
}

internal data class AndroidWriteReport(
    val writtenFiles: MutableList<File> = mutableListOf(),
)

private data class AndroidBranding(
    val image: File,
    val imageDark: File?,
    val bottomPadding: Int,
)

private fun pickAndroidBranding(branding: ResolvedBranding?): AndroidBranding? {
    if (branding == null) return null
    val img = branding.androidImage ?: branding.image ?: return null
    val imgDark = branding.androidImageDark ?: branding.imageDark
    val padding = branding.androidBottomPadding ?: branding.bottomPadding
    return AndroidBranding(img, imgDark, padding)
}

private fun writePerDpi(src: File, root: File, fileName: String, isDark: Boolean, report: AndroidWriteReport) {
    DPI_BUCKETS.forEach { bucket ->
        val folder = if (isDark) "drawable-night-${bucket.folder.removePrefix("drawable-")}" else bucket.folder
        val dest = File(root, "$folder/$fileName")
        writePng(resize(src, bucket.scale), dest)
        report.writtenFiles += dest
    }
}

private fun writePerDpiV31(src: File, root: File, fileName: String, isDark: Boolean, report: AndroidWriteReport) {
    DPI_BUCKETS.forEach { bucket ->
        val base = bucket.folder.removePrefix("drawable-")
        val folder = if (isDark) "drawable-night-$base-v31" else "drawable-$base-v31"
        val dest = File(root, "$folder/$fileName")
        writePng(resize(src, bucket.scale), dest)
        report.writtenFiles += dest
    }
}

private fun writeBackground(
    root: File,
    fileName: String,
    color: String?,
    backgroundImage: File?,
    isDark: Boolean,
    report: AndroidWriteReport,
) {
    val image: BufferedImage = when {
        backgroundImage != null -> ImageIO.read(backgroundImage)
            ?: error("Cannot decode ${backgroundImage.absolutePath}")

        color != null -> solidColor(color)
        else -> solidColor("ffffff")
    }
    val drawableDir = if (isDark) "drawable-night" else "drawable"
    val drawableV21Dir = if (isDark) "drawable-night-v21" else "drawable-v21"
    val a = File(root, "$drawableDir/$fileName")
    val b = File(root, "$drawableV21Dir/$fileName")
    writePng(image, a)
    writePng(image, b)
    report.writtenFiles += a
    report.writtenFiles += b
}

private fun File.write(content: String) {
    parentFile?.mkdirs()
    writeText(content, Charsets.UTF_8)
}

/** Surface a hex like `42a5f5` as `#42a5f5` so styles.xml accepts it as a color literal. */
private fun hexAsAndroidString(hex: String): String = hexToRgb(hex).toAndroidHex()

package ly.com.tahaben.kmpnativesplash.ios

import ly.com.tahaben.kmpnativesplash.image.resize
import ly.com.tahaben.kmpnativesplash.image.solidColor
import ly.com.tahaben.kmpnativesplash.image.writePng
import java.io.File
import javax.imageio.ImageIO

internal data class ImageSet(val imagesetDir: File, val baseName: String)

/** Writes 1x/2x/3x renders to an `*.imageset/` directory plus a Contents.json. */
internal fun writeImageSet(
    src: File,
    imagesetDir: File,
    baseName: String,
    dark: File? = null,
    darkBaseName: String = "${baseName}Dark",
) {
    imagesetDir.mkdirs()
    listOf(1.0, 2.0, 3.0).forEachIndexed { idx, scale ->
        val suffix = if (idx == 0) "" else "@${idx + 1}x"
        writePng(resize(src, scale), File(imagesetDir, "$baseName$suffix.png"))
    }
    dark?.let { d ->
        listOf(1.0, 2.0, 3.0).forEachIndexed { idx, scale ->
            val suffix = if (idx == 0) "" else "@${idx + 1}x"
            writePng(resize(d, scale), File(imagesetDir, "$darkBaseName$suffix.png"))
        }
    }
    val contents = if (dark != null) launchImageContentsJsonDark(baseName, darkBaseName)
    else launchImageContentsJson(baseName)
    File(imagesetDir, "Contents.json").writeText(contents, Charsets.UTF_8)
}

/**
 * Writes the LaunchBackground{V}.imageset/. The background image is **either** a 1×1 solid
 * (when only `color` is configured) **or** a copy of the user-supplied [backgroundImage].
 */
internal fun writeBackgroundImageSet(
    imagesetDir: File,
    color: String?,
    backgroundImage: File?,
    darkColor: String?,
    backgroundImageDark: File?,
) {
    imagesetDir.mkdirs()
    val main = if (backgroundImage != null) {
        ImageIO.read(backgroundImage) ?: error("Cannot decode ${backgroundImage.absolutePath}")
    } else solidColor(color ?: "ffffff")
    writePng(main, File(imagesetDir, "background.png"))

    val hasDark = backgroundImageDark != null || darkColor != null
    if (hasDark) {
        val dark = if (backgroundImageDark != null) {
            ImageIO.read(backgroundImageDark) ?: error("Cannot decode ${backgroundImageDark.absolutePath}")
        } else solidColor(darkColor ?: color ?: "000000")
        writePng(dark, File(imagesetDir, "darkbackground.png"))
        File(imagesetDir, "Contents.json").writeText(LAUNCH_BACKGROUND_CONTENTS_JSON_DARK, Charsets.UTF_8)
    } else {
        File(imagesetDir, "Contents.json").writeText(LAUNCH_BACKGROUND_CONTENTS_JSON, Charsets.UTF_8)
    }
}

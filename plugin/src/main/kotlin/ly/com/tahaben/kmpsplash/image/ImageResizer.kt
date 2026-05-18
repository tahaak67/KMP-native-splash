package ly.com.tahaben.kmpsplash.image

import ly.com.tahaben.kmpsplash.util.hexToRgb
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Resizes a 4× source image to a target density bucket. `scale` is the bucket multiplier
 * (mdpi=1, hdpi=1.5, xhdpi=2, xxhdpi=3, xxxhdpi=4 — equivalently iOS 1x/2x/3x).
 *
 * Output dimensions are `srcDim * scale / 4`, following the convention that the user
 * supplies a 4× asset.
 */
internal fun resize(src: File, scale: Double): BufferedImage {
    val input = ImageIO.read(src)
        ?: error("Cannot decode image: ${src.absolutePath}")
    val w = (input.width.toDouble() * scale / 4.0).toInt().coerceAtLeast(1)
    val h = (input.height.toDouble() * scale / 4.0).toInt().coerceAtLeast(1)
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(input, 0, 0, w, h, null)
    } finally {
        g.dispose()
    }
    return out
}

/** 1×1 PNG filled with the parsed hex color. */
internal fun solidColor(hex: String): BufferedImage {
    val rgb = hexToRgb(hex)
    val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
    img.setRGB(0, 0, Color(rgb.r, rgb.g, rgb.b).rgb)
    return img
}

internal fun writePng(image: BufferedImage, dest: File) {
    dest.parentFile?.mkdirs()
    ImageIO.write(image, "png", dest)
}

/** Decodes only enough of [file] to return its pixel dimensions, without loading pixel data. */
internal fun readDimensions(file: File): Pair<Int, Int> {
    ImageIO.createImageInputStream(file).use { stream ->
        val readers = ImageIO.getImageReaders(stream)
        if (!readers.hasNext()) error("Cannot decode image: ${file.absolutePath}")
        val reader = readers.next()
        try {
            reader.input = stream
            return reader.getWidth(0) to reader.getHeight(0)
        } finally {
            reader.dispose()
        }
    }
}

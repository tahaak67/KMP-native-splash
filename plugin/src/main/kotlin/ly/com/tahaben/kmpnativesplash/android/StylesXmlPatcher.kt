package ly.com.tahaben.kmpnativesplash.android

import ly.com.tahaben.kmpnativesplash.util.childElements
import ly.com.tahaben.kmpnativesplash.util.parseXmlString
import ly.com.tahaben.kmpnativesplash.util.writeXml
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File

internal data class StylesPatchOptions(
    val fullscreen: Boolean,
    val a12: A12Options? = null,
) {
    data class A12Options(
        val backgroundColor: String?,
        val iconBackgroundColor: String?,
        val hasAnimatedIcon: Boolean,
        val hasBrandingImage: Boolean,
    )
}

/**
 * Renders a styles.xml from the appropriate template, applies the runtime patches
 * (fullscreen / cutout / A12 attributes), and writes it to disk.
 */
internal fun renderStyles(template: String, opts: StylesPatchOptions, out: File) {
    val doc = parseXmlString(template)
    val launchTheme = findStyle(doc, "LaunchTheme") ?: return
    patchLaunchTheme(launchTheme, doc, opts)
    writeXml(doc, out)
}

private fun findStyle(doc: Document, name: String): Element? =
    doc.documentElement.childElements().firstOrNull { it.tagName == "style" && it.getAttribute("name") == name }

private fun patchLaunchTheme(style: Element, doc: Document, opts: StylesPatchOptions) {
    setItem(doc, style, "android:forceDarkAllowed", "false")
    setItem(doc, style, "android:windowFullscreen", opts.fullscreen.toString())
    // Decoupled from `fullscreen`: tying this to fullscreen=false wrote
    // windowDrawsSystemBarBackgrounds=false, which overrides the theme default and
    // makes the status/navigation bars render as solid black bars. Always true so the
    // window keeps drawing the (themed/translucent) system-bar backgrounds; fullscreen
    // is still controlled solely by windowFullscreen above.
    setItem(doc, style, "android:windowDrawsSystemBarBackgrounds", "true")
    setItem(doc, style, "android:windowLayoutInDisplayCutoutMode", "shortEdges")

    val a12 = opts.a12
    if (a12 != null) {
        if (a12.backgroundColor != null) {
            setItem(doc, style, "android:windowSplashScreenBackground", a12.backgroundColor)
        } else removeItem(style, "android:windowSplashScreenBackground")
        if (a12.hasAnimatedIcon) {
            setItem(doc, style, "android:windowSplashScreenAnimatedIcon", "@drawable/android12splash")
        } else removeItem(style, "android:windowSplashScreenAnimatedIcon")
        if (a12.iconBackgroundColor != null) {
            setItem(doc, style, "android:windowSplashScreenIconBackgroundColor", a12.iconBackgroundColor)
        } else removeItem(style, "android:windowSplashScreenIconBackgroundColor")
        if (a12.hasBrandingImage) {
            setItem(doc, style, "android:windowSplashScreenBrandingImage", "@drawable/android12branding")
        } else removeItem(style, "android:windowSplashScreenBrandingImage")
    } else {
        removeItem(style, "android:windowSplashScreenBackground")
        removeItem(style, "android:windowSplashScreenAnimatedIcon")
        removeItem(style, "android:windowSplashScreenIconBackgroundColor")
        removeItem(style, "android:windowSplashScreenBrandingImage")
    }
}

private fun setItem(doc: Document, style: Element, attrName: String, value: String) {
    val existing = style.childElements().firstOrNull { it.tagName == "item" && it.getAttribute("name") == attrName }
    if (existing != null) {
        existing.textContent = value
    } else {
        val node = doc.createElement("item").apply {
            setAttribute("name", attrName)
            textContent = value
        }
        style.appendChild(node)
    }
}

private fun removeItem(style: Element, attrName: String) {
    val existing = style.childElements().firstOrNull { it.tagName == "item" && it.getAttribute("name") == attrName }
        ?: return
    style.removeChild(existing)
}

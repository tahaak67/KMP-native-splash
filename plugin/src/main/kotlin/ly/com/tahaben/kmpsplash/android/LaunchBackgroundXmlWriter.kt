package ly.com.tahaben.kmpsplash.android

import ly.com.tahaben.kmpsplash.dsl.BrandingMode

internal data class LaunchBackgroundOptions(
    val hasForegroundImage: Boolean,
    val gravity: String,
    val branding: BrandingItem? = null,
) {
    data class BrandingItem(
        val mode: BrandingMode,
        val bottomPaddingDp: Int,
    )
}

/**
 * Produces `launch_background.xml` by concatenating string template fragments. We rebuild it
 * from scratch on every run (rather than patching an existing one) because the file is
 * fully generator-owned.
 */
internal fun renderLaunchBackgroundXml(opts: LaunchBackgroundOptions): String {
    val items = StringBuilder()
    items.append(launchItemBitmap("fill", "background"))
    if (opts.hasForegroundImage) {
        items.append(launchItemBitmap(opts.gravity, "splash"))
    }
    opts.branding?.let { brand ->
        val gravity = when (brand.mode) {
            BrandingMode.BOTTOM -> "bottom|center_horizontal"
            BrandingMode.BOTTOM_LEFT -> "bottom|left"
            BrandingMode.BOTTOM_RIGHT -> "bottom|right"
        }
        if (gravity != opts.gravity) {
            items.append(brandingItemBitmap(gravity, brand.bottomPaddingDp, "branding"))
        }
    }
    return ANDROID_LAUNCH_BACKGROUND_XML.replace(
        "</layer-list>",
        items.toString() + "</layer-list>",
    )
}

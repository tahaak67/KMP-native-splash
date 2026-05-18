package ly.com.tahaben.kmpsplash.ios

import ly.com.tahaben.kmpsplash.dsl.BrandingMode
import ly.com.tahaben.kmpsplash.image.readDimensions
import java.io.File

internal data class StoryboardOptions(
    val launchImageName: String,
    val launchBackgroundName: String,
    val contentMode: String,
    val foregroundImage: File?,
    val brandingImageName: String?,
    val brandingMode: BrandingMode = BrandingMode.BOTTOM,
    val brandingBottomPadding: Int = 0,
)

/**
 * Renders `LaunchScreen{V}.storyboard` by substituting placeholders in the baseline template
 * and optionally injecting a branding `<imageView>`. The file is fully generator-owned
 * (we never edit a user-authored storyboard).
 */
internal fun writeStoryboard(opts: StoryboardOptions, destination: File) {
    destination.parentFile?.mkdirs()
    val (imgW, imgH) = opts.foregroundImage?.let { runCatching { readDimensions(it) }.getOrNull() } ?: (200 to 200)
    var content = LAUNCH_SCREEN_STORYBOARD
        .replace("{LAUNCH_IMAGE}", opts.launchImageName)
        .replace("{LAUNCH_BACKGROUND}", opts.launchBackgroundName)
        .replace("{CONTENT_MODE}", opts.contentMode)
        .replace("{IMG_W}", imgW.toString())
        .replace("{IMG_H}", imgH.toString())
        .replace("{BG_W}", "1")
        .replace("{BG_H}", "1")

    if (opts.brandingImageName != null) {
        val brandingView = brandingImageViewXml(opts.brandingImageName, opts.brandingMode)
        val brandingConstraints = brandingConstraintsXml(opts.brandingMode, opts.brandingBottomPadding)
        // Inject after the launch-image imageView, and add constraints before </constraints>.
        content = content.replace(
            "</subviews>",
            "    $brandingView\n                        </subviews>",
        ).replace(
            "</constraints>",
            "    $brandingConstraints\n                        </constraints>",
        )
        // Add a <resources> entry for the branding image.
        content = content.replace(
            "</resources>",
            """    <image name="${opts.brandingImageName}" width="100" height="100"/>
    </resources>""",
        )
    }
    destination.writeText(content, Charsets.UTF_8)
}

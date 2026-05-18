package ly.com.tahaben.kmpsplash.dsl

object AndroidGravity {
    const val FILL = "fill"
    const val CENTER = "center"
    const val TOP = "top"
    const val BOTTOM = "bottom"
    const val LEFT = "left"
    const val RIGHT = "right"
    const val CENTER_HORIZONTAL = "center_horizontal"
    const val CENTER_VERTICAL = "center_vertical"
    const val START = "start"
    const val END = "end"
    const val CLIP_HORIZONTAL = "clip_horizontal"
    const val CLIP_VERTICAL = "clip_vertical"
    const val FILL_HORIZONTAL = "fill_horizontal"
    const val FILL_VERTICAL = "fill_vertical"

    val ALL: Set<String> = setOf(
        FILL, CENTER, TOP, BOTTOM, LEFT, RIGHT,
        CENTER_HORIZONTAL, CENTER_VERTICAL, START, END,
        CLIP_HORIZONTAL, CLIP_VERTICAL, FILL_HORIZONTAL, FILL_VERTICAL,
    )
}

object IosContentMode {
    const val SCALE_TO_FILL = "scaleToFill"
    const val SCALE_ASPECT_FIT = "scaleAspectFit"
    const val SCALE_ASPECT_FILL = "scaleAspectFill"
    const val CENTER = "center"
    const val TOP = "top"
    const val BOTTOM = "bottom"
    const val LEFT = "left"
    const val RIGHT = "right"

    val ALL: Set<String> = setOf(
        SCALE_TO_FILL, SCALE_ASPECT_FIT, SCALE_ASPECT_FILL,
        CENTER, TOP, BOTTOM, LEFT, RIGHT,
    )
}

object AndroidScreenOrientation {
    val ALL: Set<String> = setOf(
        "unspecified", "behind", "landscape", "portrait", "reverseLandscape",
        "reversePortrait", "sensorLandscape", "sensorPortrait", "userLandscape",
        "userPortrait", "sensor", "fullSensor", "nosensor", "user", "fullUser", "locked",
    )
}

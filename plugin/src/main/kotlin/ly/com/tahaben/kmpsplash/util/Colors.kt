package ly.com.tahaben.kmpsplash.util

/**
 * Normalizes hex strings like "#42a5f5" or "42a5f5" (or "#FFFFFFFF" / 8-digit ARGB) to a
 * lowercase 6-digit RRGGBB string (no `#`).
 *
 * Throws [IllegalArgumentException] for inputs that don't fit the expected shape.
 */
internal fun parseColor(input: String): String {
    val s = input.trim().removePrefix("#")
    return when (s.length) {
        6 -> if (s.matches(HEX6)) s.lowercase() else fail(input)
        8 -> if (s.matches(HEX8)) s.substring(2).lowercase() else fail(input)
        3 -> if (s.matches(HEX3)) s.map { "$it$it" }.joinToString("").lowercase() else fail(input)
        else -> fail(input)
    }
}

private val HEX3 = Regex("[0-9a-fA-F]{3}")
private val HEX6 = Regex("[0-9a-fA-F]{6}")
private val HEX8 = Regex("[0-9a-fA-F]{8}")

private fun fail(raw: String): Nothing =
    throw IllegalArgumentException("Invalid color '$raw' — expected #RGB, #RRGGBB, or #AARRGGBB")

internal data class Rgb(val r: Int, val g: Int, val b: Int) {
    fun toAndroidHex(): String = "#%02x%02x%02x".format(r, g, b)
}

internal fun hexToRgb(hex: String): Rgb {
    val s = parseColor(hex)
    return Rgb(
        s.substring(0, 2).toInt(16),
        s.substring(2, 4).toInt(16),
        s.substring(4, 6).toInt(16),
    )
}

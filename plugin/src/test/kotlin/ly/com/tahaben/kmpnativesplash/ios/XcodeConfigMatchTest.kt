package ly.com.tahaben.kmpnativesplash.ios

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Regression coverage for [lookupStoryboardForConfig]. The original matcher checked
 * tokens in reverse, so a `{flavor}Debug` configuration (KMP-Flavorizr / xcconfig
 * convention, e.g. `devDebug`) hit the build-type token `Debug` first and resolved to
 * the default `LaunchScreen` — every flavor showed the default at runtime.
 */
class XcodeConfigMatchTest {

    // Mirrors WireXcodeFlavorsTask.buildStoryboardMap for variants default/dev/prod.
    private val seeded: Map<String, String> = buildMap {
        listOf("dev", "prod").forEach { name ->
            val cap = name.replaceFirstChar { it.uppercaseChar() }
            val sb = "LaunchScreen$cap"
            put(name, sb)
            put("Debug-$name", sb); put("Release-$name", sb); put("Profile-$name", sb)
            put("${name}Debug", sb); put("${name}Release", sb); put("${name}Profile", sb)
            put("${cap}Debug", sb); put("${cap}Release", sb); put("${cap}Profile", sb)
        }
        put("Debug", "LaunchScreen")
        put("Release", "LaunchScreen")
        put("Profile", "LaunchScreen")
        put("default", "LaunchScreen")
    }

    private fun resolve(config: String) = lookupStoryboardForConfig(config, seeded)

    @Test
    fun `KMP-Flavorizr {flavor}{buildType} configs resolve to the flavor storyboard`() {
        assertEquals("LaunchScreenDev", resolve("devDebug"))
        assertEquals("LaunchScreenDev", resolve("devRelease"))
        assertEquals("LaunchScreenDev", resolve("devProfile"))
        assertEquals("LaunchScreenProd", resolve("prodDebug"))
        assertEquals("LaunchScreenProd", resolve("prodRelease"))
    }

    @Test
    fun `dash-separated and spaced conventions resolve to the flavor storyboard`() {
        assertEquals("LaunchScreenDev", resolve("Debug-dev"))
        assertEquals("LaunchScreenProd", resolve("Release-prod"))
        assertEquals("LaunchScreenDev", resolve("Dev Debug"))
        assertEquals("LaunchScreenProd", resolve("PROD_RELEASE"))
    }

    @Test
    fun `plain build types and unknown configs fall back to the default storyboard`() {
        assertEquals("LaunchScreen", resolve("Debug"))
        assertEquals("LaunchScreen", resolve("Release"))
        assertEquals("LaunchScreen", resolve("Staging Debug"))
    }
}

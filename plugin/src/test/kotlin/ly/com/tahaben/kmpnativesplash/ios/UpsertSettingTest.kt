package ly.com.tahaben.kmpnativesplash.ios

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Regression coverage for [upsertSetting]. The String-replacement overload of
 * `Regex.replace` parses `$`/`\` in the replacement, so an existing key whose new value
 * is `$(LAUNCH_SCREEN_STORYBOARD)` threw `IllegalArgumentException: Illegal group
 * reference` — failing `wireXcodeFlavors` on every project that already has
 * `INFOPLIST_KEY_UILaunchStoryboardName`.
 */
class UpsertSettingTest {

    @Test
    fun `inserts a new setting when the key is absent`() {
        val out = upsertSetting("\n\t\t\t\tSDKROOT = iphoneos;\n\t\t\t", "LAUNCH_SCREEN_STORYBOARD", "LaunchScreenDev")
        assertTrue(out.contains("SDKROOT = iphoneos;"))
        assertTrue(out.contains("LAUNCH_SCREEN_STORYBOARD = \"LaunchScreenDev\";"))
    }

    @Test
    fun `replaces an existing setting without duplicating it`() {
        val bs = "\n\t\t\t\tLAUNCH_SCREEN_STORYBOARD = LaunchScreen;\n\t\t\t\tSDKROOT = iphoneos;\n\t\t\t"
        val out = upsertSetting(bs, "LAUNCH_SCREEN_STORYBOARD", "LaunchScreenProd")
        assertTrue(out.contains("LAUNCH_SCREEN_STORYBOARD = \"LaunchScreenProd\";"))
        assertFalse(out.contains("LaunchScreen;")) // old bare value gone
        assertEquals(1, Regex("LAUNCH_SCREEN_STORYBOARD").findAll(out).count())
    }

    @Test
    fun `value containing the build-setting reference is treated literally (no Illegal group reference)`() {
        val bs = "\n\t\t\t\tINFOPLIST_KEY_UILaunchStoryboardName = LaunchScreen;\n\t\t\t\tSDKROOT = iphoneos;\n\t\t\t"
        // Must not throw IllegalArgumentException.
        val out = upsertSetting(bs, "INFOPLIST_KEY_UILaunchStoryboardName", "\$(LAUNCH_SCREEN_STORYBOARD)")
        assertTrue(out.contains("INFOPLIST_KEY_UILaunchStoryboardName = \"\$(LAUNCH_SCREEN_STORYBOARD)\";"))
        assertTrue(out.contains("SDKROOT = iphoneos;"))
    }

    @Test
    fun `inserting a build-setting-reference value when key is absent is also literal`() {
        val out = upsertSetting(
            "\n\t\t\t\tSDKROOT = iphoneos;\n\t\t\t",
            "INFOPLIST_KEY_UILaunchStoryboardName",
            "\$(LAUNCH_SCREEN_STORYBOARD)"
        )
        assertTrue(out.contains("INFOPLIST_KEY_UILaunchStoryboardName = \"\$(LAUNCH_SCREEN_STORYBOARD)\";"))
    }
}

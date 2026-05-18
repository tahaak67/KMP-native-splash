package ly.com.tahaben.kmpnativesplash.tasks

import ly.com.tahaben.kmpnativesplash.ios.PatchPlan
import ly.com.tahaben.kmpnativesplash.ios.patchPbxproj
import ly.com.tahaben.kmpnativesplash.ios.setUILaunchStoryboardName
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

/**
 * Opt-in iOS auto-wiring: patches `project.pbxproj` to register per-flavor
 * `LaunchScreen{V}.storyboard` files and add `LAUNCH_SCREEN_STORYBOARD` build settings,
 * and points each Info.plist's `UILaunchStoryboardName` at `$(LAUNCH_SCREEN_STORYBOARD)`.
 *
 * Skipped entirely (`onlyIf`) when `kmpNativeSplash.ios.autoWireXcodeFlavors` is false.
 * The Xcode project must be committed before this task runs — see plan §4.6.
 */
abstract class WireXcodeFlavorsTask : DefaultTask() {

    @get:Input
    abstract val autoWire: Property<Boolean>

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val xcodeproj: DirectoryProperty

    @get:Internal
    abstract val iosAppDir: DirectoryProperty

    @get:Input
    abstract val infoPlistFiles: ListProperty<String>

    /** All splash variant names (including "default"). */
    @get:Input
    abstract val variantNames: ListProperty<String>

    @TaskAction
    fun run() {
        if (!autoWire.getOrElse(false)) return
        val xcodeprojDir = xcodeproj.asFile.orNull
            ?: error("kmpNativeSplash.ios.autoWireXcodeFlavors=true but xcodeproj path is unset.")
        val pbxproj = File(xcodeprojDir, "project.pbxproj")
        require(pbxproj.exists()) { "project.pbxproj not found at ${pbxproj.absolutePath}" }

        val storyboardByConfig = buildStoryboardMap()
        val allStoryboards = storyboardByConfig.values.toSet()
        // Compute the storyboards' directory relative to the `.xcodeproj`'s parent dir
        // (Xcode's SOURCE_ROOT). Storyboards are written to `<iosAppDir>/Base.lproj/`, so
        // this lets the patcher emit a PBXFileReference path that resolves regardless of
        // which group the file ref ends up in.
        val sourceRoot = xcodeprojDir.parentFile ?: error("xcodeproj has no parent: ${xcodeprojDir.absolutePath}")
        val storyboardsDir = File(iosAppDir.asFile.get(), "Base.lproj")
        val relDir = sourceRoot.toPath().relativize(storyboardsDir.toPath())
            .toString().replace(File.separatorChar, '/')
        val report = patchPbxproj(pbxproj, PatchPlan(storyboardByConfig, allStoryboards, relDir))

        logger.lifecycle("[kmp-native-splash] pbxproj patched: ${report.addedReferences.size} new file refs, ${report.updatedConfigurations.size} configurations updated.")
        report.configStoryboard.forEach { (cfg, sb) ->
            logger.lifecycle("[kmp-native-splash]   config '$cfg' → $sb.storyboard")
        }
        if (report.infoPlistKeyConfigurations.isNotEmpty()) {
            logger.lifecycle(
                "[kmp-native-splash]   routed INFOPLIST_KEY_UILaunchStoryboardName via " +
                        "\$(LAUNCH_SCREEN_STORYBOARD) for: ${
                            report.infoPlistKeyConfigurations.distinct().joinToString()
                        }",
            )
        }

        // Diagnose the #1 failure mode: per-flavor storyboards generated, but no Xcode
        // *build configuration* maps to them, so every flavor runs the default at runtime.
        val flavorStoryboards = variantNames.get()
            .filter { it != "default" }
            .map { "LaunchScreen" + it.replaceFirstChar { c -> c.uppercaseChar() } }
            .toSet()
        if (flavorStoryboards.isNotEmpty() && report.configStoryboard.values.none { it in flavorStoryboards }) {
            logger.warn(
                "[kmp-native-splash] WARNING: every Xcode build configuration resolved to the " +
                        "default 'LaunchScreen' — per-flavor launch screens will NOT appear at runtime.\n" +
                        "  Generated per-flavor storyboards: ${flavorStoryboards.sorted().joinToString()}\n" +
                        "  Xcode configurations found:       ${
                            report.configStoryboard.keys.joinToString().ifEmpty { "<none>" }
                        }\n" +
                        "  Per-flavor launch screens require per-flavor Xcode *build configurations* (e.g. " +
                        "'Debug-dev'/'Release-dev', 'Debug-prod'/'Release-prod') AND schemes that build with " +
                        "them. This plugin maps configurations to storyboards by name suffix; it does not " +
                        "create configurations or schemes. In Xcode: duplicate Debug/Release per flavor, " +
                        "suffix each with the flavor name, point each scheme at its config, then re-run " +
                        "generateNativeSplash and run the matching scheme.",
            )
        }

        val plistRoots = infoPlistFiles.getOrElse(listOf("Info.plist")).ifEmpty { listOf("Info.plist") }
        val missingPlists = mutableListOf<File>()
        plistRoots.forEach { relative ->
            val plist = File(iosAppDir.asFile.get(), relative)
            if (!setUILaunchStoryboardName(plist, "\$(LAUNCH_SCREEN_STORYBOARD)")) missingPlists += plist
        }
        if (missingPlists.isNotEmpty()) {
            val handledViaBuildSetting = report.usesGeneratedInfoPlist || report.infoPlistKeyConfigurations.isNotEmpty()
            val note = if (handledViaBuildSetting) {
                " Project generates Info.plist from build settings — routed " +
                        "INFOPLIST_KEY_UILaunchStoryboardName through \$(LAUNCH_SCREEN_STORYBOARD) instead, so this is fine."
            } else {
                " UILaunchStoryboardName indirection was NOT applied — the launch screen cannot switch per " +
                        "configuration. Set kmpNativeSplash { ios { infoPlistFiles.set(listOf(\"<path/to/Info.plist>\")) } }" +
                        "or, if you use GENERATE_INFOPLIST_FILE, ensure INFOPLIST_KEY_UILaunchStoryboardName exists so the plugin can route it."
            }
            logger.warn("[kmp-native-splash] Info.plist not found: ${missingPlists.joinToString { it.absolutePath }}.$note")
        }
    }

    /**
     * Map each Xcode configuration name to the storyboard it should reference. Default mapping:
     *  - "Debug"/"Release" → `LaunchScreen` (the default-variant storyboard).
     *  - "Debug-<flavor>" / "<flavor>Debug" / `<Flavor>Release` → `LaunchScreen<Flavor>`.
     *
     * The XcodeProjectPatcher does fuzzy suffix matching too, so callers don't need exhaustive
     * keys; we just seed the map with what we know.
     */
    private fun buildStoryboardMap(): Map<String, String> {
        val map = linkedMapOf<String, String>()
        variantNames.get().forEach { name ->
            val suffix = if (name == "default") "" else name.replaceFirstChar { it.uppercaseChar() }
            val storyboard = "LaunchScreen$suffix"
            val cap = name.replaceFirstChar { it.uppercaseChar() }
            // Common conventions seeded for the matcher in the patcher. Includes the
            // KMP-Flavorizr convention `{flavor}Debug` / `{flavor}Release` /
            // `{flavor}Profile` (xcconfig-derived configuration names), plus the
            // `Debug-<flavor>` / `Release-<flavor>` form and capitalized variants.
            map[name] = storyboard
            map["Debug-$name"] = storyboard
            map["Release-$name"] = storyboard
            map["Profile-$name"] = storyboard
            map["${name}Debug"] = storyboard
            map["${name}Release"] = storyboard
            map["${name}Profile"] = storyboard
            map["${cap}Debug"] = storyboard
            map["${cap}Release"] = storyboard
            map["${cap}Profile"] = storyboard
        }
        // Always provide a default mapping for plain Debug/Release/Profile.
        map["Debug"] = map["Debug"] ?: "LaunchScreen"
        map["Release"] = map["Release"] ?: "LaunchScreen"
        map["Profile"] = map["Profile"] ?: "LaunchScreen"
        map["default"] = map["default"] ?: "LaunchScreen"
        return map
    }
}

package ly.com.tahaben.kmpnativesplash.tasks

import ly.com.tahaben.kmpnativesplash.ResolvedAndroid
import ly.com.tahaben.kmpnativesplash.ResolvedBranding
import ly.com.tahaben.kmpnativesplash.ResolvedIos
import ly.com.tahaben.kmpnativesplash.ResolvedSplashSpec
import ly.com.tahaben.kmpnativesplash.dsl.BrandingMode
import ly.com.tahaben.kmpnativesplash.ios.writeIosSplash
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class GenerateIosSplashTask : DefaultTask() {

    @get:Input
    abstract val variantName: Property<String>

    /** True if this task represents the default variant (controls output suffix). */
    @get:Input
    abstract val defaultVariantInput: Property<Boolean>

    @get:Input
    abstract val fullscreen: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val color: Property<String>

    @get:Input
    @get:Optional
    abstract val colorDark: Property<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val image: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val imageDark: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val backgroundImage: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val backgroundImageDark: RegularFileProperty

    @get:Input
    abstract val contentMode: Property<String>

    @get:Input
    abstract val infoPlistFiles: ListProperty<String>

    /**
     * Mirror of `kmpNativeSplash.ios.autoWireXcodeFlavors`. When false for a non-default
     * variant, `wireXcodeFlavors` is SKIPPED and the generated per-flavor storyboard is
     * never connected to Xcode — the task warns so this isn't a silent no-op.
     */
    @get:Input
    @get:Optional
    abstract val autoWireXcodeFlavors: Property<Boolean>

    // Branding
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val brandingImage: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val brandingImageDark: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val brandingMode: Property<BrandingMode>

    @get:Input
    @get:Optional
    abstract val brandingBottomPadding: Property<Int>

    /** The iOS app's root directory: contains Info.plist, Assets.xcassets, Base.lproj/. */
    @get:OutputDirectory
    abstract val iosAppDir: DirectoryProperty

    @TaskAction
    fun run() {
        val branding = buildBranding()
        val variant = variantName.get()
        val out = iosAppDir.asFile.get()
        val colorVal = color.orNull
        val imageVal = image.asFile.orNull
        val backgroundImageVal = backgroundImage.asFile.orNull
        val hasAnyInput = colorVal != null || imageVal != null || backgroundImageVal != null

        logger.lifecycle("[kmp-native-splash] iOS · variant='$variant' → ${out.absolutePath}")
        logger.lifecycle(
            "[kmp-native-splash]   color=${colorVal ?: "<none>"}, colorDark=${colorDark.orNull ?: "<none>"}, " +
                    "image=${imageVal?.name ?: "<none>"}, imageDark=${imageDark.asFile.orNull?.name ?: "<none>"}",
        )
        if (backgroundImageVal != null || backgroundImageDark.asFile.orNull != null) {
            logger.lifecycle(
                "[kmp-native-splash]   backgroundImage=${backgroundImageVal?.name ?: "<none>"}, " +
                        "backgroundImageDark=${backgroundImageDark.asFile.orNull?.name ?: "<none>"}",
            )
        }
        if (branding != null) {
            logger.lifecycle("[kmp-native-splash]   branding mode=${branding.mode} bottomPadding=${branding.bottomPadding} image=${branding.image?.name ?: "<none>"}")
        }
        logger.lifecycle(
            "[kmp-native-splash]   contentMode=${contentMode.get()}, fullscreen=${fullscreen.get()}, plists=${
                infoPlistFiles.getOrElse(
                    listOf("Info.plist")
                )
            }"
        )
        if (!hasAnyInput) {
            logger.warn("[kmp-native-splash] WARNING variant='$variant' has no color/image/backgroundImage configured — generating a default white splash.")
        }
        if (!out.exists()) {
            logger.warn("[kmp-native-splash] iOS app directory does not exist: ${out.absolutePath} (it will be created). Set `kmpNativeSplash.ios.appDir` (or `ios.projectPath` / `ios.targetName`) if your iOS app lives elsewhere.")
        }

        val spec = ResolvedSplashSpec(
            variantName = variantName.get(),
            isDefaultVariant = defaultVariantInput.get(),
            color = color.orNull,
            colorDark = colorDark.orNull,
            image = image.asFile.orNull,
            imageDark = imageDark.asFile.orNull,
            backgroundImage = backgroundImage.asFile.orNull,
            backgroundImageDark = backgroundImageDark.asFile.orNull,
            fullscreen = fullscreen.get(),
            branding = branding,
            android = ResolvedAndroid(
                color = null, colorDark = null, image = null, imageDark = null,
                backgroundImage = null, backgroundImageDark = null,
                gravity = "center", screenOrientation = null,
                applyManifestTheme = false, themeName = "@style/LaunchTheme",
            ),
            ios = ResolvedIos(
                color = color.orNull,
                colorDark = colorDark.orNull,
                image = image.asFile.orNull,
                imageDark = imageDark.asFile.orNull,
                backgroundImage = backgroundImage.asFile.orNull,
                backgroundImageDark = backgroundImageDark.asFile.orNull,
                contentMode = contentMode.get(),
                infoPlistFiles = infoPlistFiles.getOrElse(listOf("Info.plist")),
                autoWireXcodeFlavors = false,
            ),
            android12 = null,
        )

        val report = writeIosSplash(spec, out)
        logger.lifecycle(
            "[kmp-native-splash]   wrote ${report.imageSets.size} imageset(s) + storyboard ${report.storyboard?.name ?: "(none)"}",
        )
        report.imageSets.forEach { logger.lifecycle("[kmp-native-splash]     · ${it.relativeTo(out)}") }
        report.storyboard?.let { logger.lifecycle("[kmp-native-splash]     · ${it.relativeTo(out)}") }
        if (report.patchedPlists.isNotEmpty()) {
            logger.lifecycle("[kmp-native-splash]   patched plist(s): ${report.patchedPlists.joinToString { it.name }}")
        }
        if (report.skippedPlists.isNotEmpty()) {
            logger.warn(
                "[kmp-native-splash] plist(s) not found and skipped: ${report.skippedPlists.joinToString { it.absolutePath }}",
            )
        }
        if (!defaultVariantInput.get() && !autoWireXcodeFlavors.getOrElse(false)) {
            logger.warn(
                "[kmp-native-splash] WARNING variant='$variant': generated " +
                        "${report.storyboard?.name ?: "the per-flavor storyboard"} but Xcode is NOT wired — " +
                        "`kmpNativeSplash.ios.autoWireXcodeFlavors` is disabled, so the `wireXcodeFlavors` task is " +
                        "SKIPPED and iOS will keep showing the DEFAULT LaunchScreen for every flavor.\n" +
                        "  Fix: kmpNativeSplash { ios { autoWireXcodeFlavors = true } }  — commit your Xcode project" +
                        "first, it patches project.pbxproj.\n" +
                        "  Manual alternative: set UILaunchStoryboardName = \$(LAUNCH_SCREEN_STORYBOARD) and add a " +
                        "per-configuration LAUNCH_SCREEN_STORYBOARD build setting in Xcode.",
            )
        }
    }

    private fun buildBranding(): ResolvedBranding? {
        val img = brandingImage.asFile.orNull
        val imgDark = brandingImageDark.asFile.orNull
        if (img == null && imgDark == null) return null
        return ResolvedBranding(
            image = img,
            imageDark = imgDark,
            mode = brandingMode.getOrElse(BrandingMode.BOTTOM),
            bottomPadding = brandingBottomPadding.getOrElse(0),
            androidImage = null,
            androidImageDark = null,
            androidBottomPadding = null,
            iosImage = img,
            iosImageDark = imgDark,
            iosBottomPadding = brandingBottomPadding.orNull,
        )
    }
}

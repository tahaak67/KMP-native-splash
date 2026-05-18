package ly.com.tahaben.kmpsplash.tasks

import ly.com.tahaben.kmpsplash.*
import ly.com.tahaben.kmpsplash.android.patchManifest
import ly.com.tahaben.kmpsplash.android.writeAndroidSplash
import ly.com.tahaben.kmpsplash.dsl.BrandingMode
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

abstract class GenerateAndroidSplashTask : DefaultTask() {

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
    abstract val gravity: Property<String>
    @get:Input
    @get:Optional
    abstract val screenOrientation: Property<String>
    @get:Input
    abstract val applyManifestTheme: Property<Boolean>
    @get:Input
    abstract val themeName: Property<String>

    // ---- Branding (optional block) ----
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

    // ---- Android 12 (optional block) ----
    @get:Input
    abstract val hasAndroid12Block: Property<Boolean>
    @get:Input
    @get:Optional
    abstract val a12Color: Property<String>
    @get:Input
    @get:Optional
    abstract val a12ColorDark: Property<String>
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val a12Image: RegularFileProperty
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val a12ImageDark: RegularFileProperty
    @get:Input
    @get:Optional
    abstract val a12IconBackgroundColor: Property<String>
    @get:Input
    @get:Optional
    abstract val a12IconBackgroundColorDark: Property<String>
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val a12BrandingImage: RegularFileProperty
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val a12BrandingImageDark: RegularFileProperty

    /** Manifest to patch (`screenOrientation`). Optional — KMP libraries may have none. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidManifest: RegularFileProperty

    /** Where the generated res tree lands. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /**
     * Set only when Android-module resolution failed (Ambiguous / NotFound). The task
     * fails fast with this message so a misconfigured `androidModule` breaks this task
     * and not `build`/codegen. See [ly.com.tahaben.kmpsplash.resolve.AndroidModuleResolver].
     */
    @get:Input
    @get:Optional
    abstract val androidResolutionError: Property<String>

    @get:Input
    abstract val plistFiles: ListProperty<String> // unused on Android, accepted for symmetry

    @TaskAction
    fun run() {
        androidResolutionError.orNull?.let { throw org.gradle.api.GradleException(it) }
        val branding = buildBranding()
        val variant = variantName.get()
        val out = outputDir.asFile.get()
        val colorVal = color.orNull
        val imageVal = image.asFile.orNull
        val backgroundImageVal = backgroundImage.asFile.orNull
        val hasAnyInput = colorVal != null || imageVal != null || backgroundImageVal != null

        logger.lifecycle("[kmp-native-splash] Android · variant='$variant' → ${out.absolutePath}")
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
        if (hasAndroid12Block.get()) {
            logger.lifecycle(
                "[kmp-native-splash]   android12: color=${a12Color.orNull ?: "<none>"}, " +
                        "image=${a12Image.asFile.orNull?.name ?: "<none>"}, " +
                        "iconBgColor=${a12IconBackgroundColor.orNull ?: "<none>"}",
            )
        }
        logger.lifecycle("[kmp-native-splash]   gravity=${gravity.get()}, fullscreen=${fullscreen.get()}, screenOrientation=${screenOrientation.orNull ?: "<unchanged>"}")
        if (!hasAnyInput) {
            logger.warn("[kmp-native-splash] WARNING variant='$variant' has no color/image/backgroundImage configured — generating a default white splash.")
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
                color = color.orNull,
                colorDark = colorDark.orNull,
                image = image.asFile.orNull,
                imageDark = imageDark.asFile.orNull,
                backgroundImage = backgroundImage.asFile.orNull,
                backgroundImageDark = backgroundImageDark.asFile.orNull,
                gravity = gravity.get(),
                screenOrientation = screenOrientation.orNull,
                applyManifestTheme = applyManifestTheme.getOrElse(true),
                themeName = themeName.getOrElse("@style/LaunchTheme"),
            ),
            // iOS values are irrelevant for this task but the spec carries them — fill in stubs.
            ios = ResolvedIos(
                color = null, colorDark = null,
                image = null, imageDark = null,
                backgroundImage = null, backgroundImageDark = null,
                contentMode = "scaleAspectFit",
                infoPlistFiles = plistFiles.getOrElse(emptyList()),
                autoWireXcodeFlavors = false,
            ),
            android12 = if (hasAndroid12Block.get()) ResolvedAndroid12(
                color = a12Color.orNull,
                colorDark = a12ColorDark.orNull,
                image = a12Image.asFile.orNull,
                imageDark = a12ImageDark.asFile.orNull,
                iconBackgroundColor = a12IconBackgroundColor.orNull,
                iconBackgroundColorDark = a12IconBackgroundColorDark.orNull,
                brandingImage = a12BrandingImage.asFile.orNull,
                brandingImageDark = a12BrandingImageDark.asFile.orNull,
            ) else null,
        )

        out.mkdirs()
        val report = writeAndroidSplash(spec, out)
        logger.lifecycle("[kmp-native-splash]   wrote ${report.writtenFiles.size} drawable file(s) + styles.xml under ${out.absolutePath}")

        androidManifest.asFile.orNull?.let { manifestFile ->
            val themeFlag = applyManifestTheme.getOrElse(true)
            val themeRef = themeName.getOrElse("@style/LaunchTheme")
            val report = patchManifest(
                manifest = manifestFile,
                screenOrientation = screenOrientation.orNull,
                applyTheme = themeFlag,
                themeName = themeRef,
            )
            when {
                report == null -> {
                    if (screenOrientation.isPresent || themeFlag) {
                        logger.warn("[kmp-native-splash] manifest not patched (file missing): ${manifestFile.absolutePath}")
                    }
                }

                !report.foundAnyActivity -> {
                    logger.warn("[kmp-native-splash] no <activity> found in ${manifestFile.absolutePath}; manifest left untouched.")
                }

                else -> {
                    val activityKind =
                        if (report.foundLauncherActivity) "launcher activity" else "first <activity> (no MAIN/LAUNCHER found)"
                    report.orientationSet?.let {
                        logger.lifecycle("[kmp-native-splash]   patched ${manifestFile.name} on $activityKind: android:screenOrientation=$it")
                    }
                    if (report.orientationRemoved) {
                        logger.lifecycle("[kmp-native-splash]   removed android:screenOrientation from ${manifestFile.name}")
                    }
                    if (report.themeApplied != null) {
                        if (report.themeReplaced != null) {
                            logger.warn(
                                "[kmp-native-splash]   ${manifestFile.name}: android:theme replaced ${report.themeReplaced} → ${report.themeApplied} on $activityKind. " +
                                        "Set `kmpSplash { android { applyManifestTheme.set(false) } }` to keep your original theme and apply the splash via NativeSplash.installSplashScreen() instead.",
                            )
                        } else {
                            logger.lifecycle("[kmp-native-splash]   ${manifestFile.name}: android:theme=${report.themeApplied} on $activityKind")
                        }
                    } else if (report.themeUnchanged) {
                        logger.lifecycle("[kmp-native-splash]   ${manifestFile.name}: android:theme already set to $themeRef")
                    }
                }
            }
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
            androidImage = img,
            androidImageDark = imgDark,
            androidBottomPadding = brandingBottomPadding.orNull,
            iosImage = null,
            iosImageDark = null,
            iosBottomPadding = null,
        )
    }
}

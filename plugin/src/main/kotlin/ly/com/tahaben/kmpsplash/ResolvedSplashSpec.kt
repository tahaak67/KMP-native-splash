package ly.com.tahaben.kmpsplash

import ly.com.tahaben.kmpsplash.dsl.BrandingMode
import ly.com.tahaben.kmpsplash.dsl.SplashVariant
import org.gradle.api.provider.Property
import java.io.File
import java.io.Serializable

/**
 * Immutable, fully-resolved view of a variant for one platform after walking the
 * variant → default-extension inheritance chain. This is what the tasks ultimately consume.
 */
internal data class ResolvedSplashSpec(
    val variantName: String,
    val isDefaultVariant: Boolean,
    val color: String?,
    val colorDark: String?,
    val image: File?,
    val imageDark: File?,
    val backgroundImage: File?,
    val backgroundImageDark: File?,
    val fullscreen: Boolean,
    val branding: ResolvedBranding?,
    val android: ResolvedAndroid,
    val ios: ResolvedIos,
    val android12: ResolvedAndroid12?,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

internal data class ResolvedBranding(
    val image: File?,
    val imageDark: File?,
    val mode: BrandingMode,
    val bottomPadding: Int,
    val androidImage: File?,
    val androidImageDark: File?,
    val androidBottomPadding: Int?,
    val iosImage: File?,
    val iosImageDark: File?,
    val iosBottomPadding: Int?,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

internal data class ResolvedAndroid(
    val color: String?,
    val colorDark: String?,
    val image: File?,
    val imageDark: File?,
    val backgroundImage: File?,
    val backgroundImageDark: File?,
    val gravity: String,
    val screenOrientation: String?,
    val applyManifestTheme: Boolean,
    val themeName: String,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

internal data class ResolvedIos(
    val color: String?,
    val colorDark: String?,
    val image: File?,
    val imageDark: File?,
    val backgroundImage: File?,
    val backgroundImageDark: File?,
    val contentMode: String,
    val infoPlistFiles: List<String>,
    val autoWireXcodeFlavors: Boolean,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

internal data class ResolvedAndroid12(
    val color: String?,
    val colorDark: String?,
    val image: File?,
    val imageDark: File?,
    val iconBackgroundColor: String?,
    val iconBackgroundColorDark: String?,
    val brandingImage: File?,
    val brandingImageDark: File?,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

// ---- Resolver helpers ----

private fun <T> Property<T>.or(parent: Property<T>): T? =
    if (isPresent) get() else if (parent.isPresent) parent.get() else null

private fun <T> Property<T>.orValue(parent: Property<T>, default: T): T =
    if (isPresent) get() else if (parent.isPresent) parent.get() else default

/**
 * Resolves a DSL path string to a [File] against [projectDir]. Absolute paths are returned
 * verbatim; relative paths are resolved against the project directory — matching the
 * behaviour Gradle's own `file("…")` helper gives users.
 */
private fun String?.resolveAgainst(projectDir: File): File? {
    if (this.isNullOrBlank()) return null
    val f = File(this)
    return if (f.isAbsolute) f else File(projectDir, this)
}

/**
 * Walks the inheritance chain (variant → defaultVariant on the extension) to produce a
 * fully-resolved spec. Platform-specific overrides win over generic; missing values fall
 * back to the base default variant.
 *
 * @param projectDir base directory used to resolve relative path strings supplied via the
 *                   DSL (e.g. `image.set("splash_assets/logo.png")`).
 */
internal fun resolveSpec(extension: KmpSplashExtension, variant: SplashVariant, projectDir: File): ResolvedSplashSpec {
    val base = extension.defaultVariant
    val isDefault = variant.name == base.name

    val color = variant.color.or(base.color)
    val colorDark = variant.colorDark.or(base.colorDark)
    val image = variant.image.or(base.image).resolveAgainst(projectDir)
    val imageDark = variant.imageDark.or(base.imageDark).resolveAgainst(projectDir)
    val backgroundImage = variant.backgroundImage.or(base.backgroundImage).resolveAgainst(projectDir)
    val backgroundImageDark = variant.backgroundImageDark.or(base.backgroundImageDark).resolveAgainst(projectDir)
    val fullscreen = variant.fullscreen.orValue(base.fullscreen, false)

    val brandingB = variant.brandingBlock
    val brandingDefault = base.brandingBlock
    val brandingImage = brandingB.image.or(brandingDefault.image).resolveAgainst(projectDir)
    val brandingImageDark = brandingB.imageDark.or(brandingDefault.imageDark).resolveAgainst(projectDir)
    val brandingMode = brandingB.mode.orValue(brandingDefault.mode, BrandingMode.BOTTOM)
    val brandingBottomPadding = brandingB.bottomPadding.orValue(brandingDefault.bottomPadding, 0)
    val branding = if (
        brandingImage != null || brandingImageDark != null ||
        brandingB.android.image.isPresent || brandingB.ios.image.isPresent ||
        brandingDefault.android.image.isPresent || brandingDefault.ios.image.isPresent
    ) {
        ResolvedBranding(
            image = brandingImage,
            imageDark = brandingImageDark,
            mode = brandingMode,
            bottomPadding = brandingBottomPadding,
            androidImage = brandingB.android.image.or(brandingDefault.android.image).resolveAgainst(projectDir),
            androidImageDark = brandingB.android.imageDark.or(brandingDefault.android.imageDark)
                .resolveAgainst(projectDir),
            androidBottomPadding = brandingB.android.bottomPadding.or(brandingDefault.android.bottomPadding),
            iosImage = brandingB.ios.image.or(brandingDefault.ios.image).resolveAgainst(projectDir),
            iosImageDark = brandingB.ios.imageDark.or(brandingDefault.ios.imageDark).resolveAgainst(projectDir),
            iosBottomPadding = brandingB.ios.bottomPadding.or(brandingDefault.ios.bottomPadding),
        )
    } else null

    val a = variant.androidBlock
    val aBase = base.androidBlock
    val android = ResolvedAndroid(
        color = a.color.or(aBase.color) ?: color,
        colorDark = a.colorDark.or(aBase.colorDark) ?: colorDark,
        image = a.image.or(aBase.image).resolveAgainst(projectDir) ?: image,
        imageDark = a.imageDark.or(aBase.imageDark).resolveAgainst(projectDir) ?: imageDark,
        backgroundImage = a.backgroundImage.or(aBase.backgroundImage).resolveAgainst(projectDir) ?: backgroundImage,
        backgroundImageDark = a.backgroundImageDark.or(aBase.backgroundImageDark).resolveAgainst(projectDir)
            ?: backgroundImageDark,
        gravity = a.gravity.orValue(aBase.gravity, "center"),
        screenOrientation = a.screenOrientation.or(aBase.screenOrientation),
        applyManifestTheme = a.applyManifestTheme.orValue(aBase.applyManifestTheme, true),
        themeName = a.themeName.orValue(aBase.themeName, "@style/LaunchTheme"),
    )

    val i = variant.iosBlock
    val iBase = base.iosBlock
    val infoPlist = when {
        i.infoPlistFiles.isPresent && i.infoPlistFiles.get().isNotEmpty() -> i.infoPlistFiles.get()
        iBase.infoPlistFiles.isPresent && iBase.infoPlistFiles.get().isNotEmpty() -> iBase.infoPlistFiles.get()
        else -> listOf("Info.plist")
    }
    val ios = ResolvedIos(
        color = i.color.or(iBase.color) ?: color,
        colorDark = i.colorDark.or(iBase.colorDark) ?: colorDark,
        image = i.image.or(iBase.image).resolveAgainst(projectDir) ?: image,
        imageDark = i.imageDark.or(iBase.imageDark).resolveAgainst(projectDir) ?: imageDark,
        backgroundImage = i.backgroundImage.or(iBase.backgroundImage).resolveAgainst(projectDir) ?: backgroundImage,
        backgroundImageDark = i.backgroundImageDark.or(iBase.backgroundImageDark).resolveAgainst(projectDir)
            ?: backgroundImageDark,
        contentMode = i.contentMode.orValue(iBase.contentMode, "scaleAspectFit"),
        infoPlistFiles = infoPlist,
        autoWireXcodeFlavors = i.autoWireXcodeFlavors.orValue(iBase.autoWireXcodeFlavors, false),
    )

    val a12 = variant.android12Block
    val a12Base = base.android12Block
    val any12 = listOf(
        a12.color, a12.colorDark, a12.iconBackgroundColor, a12.iconBackgroundColorDark,
        a12Base.color, a12Base.colorDark, a12Base.iconBackgroundColor, a12Base.iconBackgroundColorDark,
        a12.image, a12.imageDark, a12.brandingImage, a12.brandingImageDark,
        a12Base.image, a12Base.imageDark, a12Base.brandingImage, a12Base.brandingImageDark,
    ).any { it.isPresent }

    val android12 = if (!any12) null else ResolvedAndroid12(
        color = a12.color.or(a12Base.color),
        colorDark = a12.colorDark.or(a12Base.colorDark),
        image = a12.image.or(a12Base.image).resolveAgainst(projectDir),
        imageDark = a12.imageDark.or(a12Base.imageDark).resolveAgainst(projectDir),
        iconBackgroundColor = a12.iconBackgroundColor.or(a12Base.iconBackgroundColor),
        iconBackgroundColorDark = a12.iconBackgroundColorDark.or(a12Base.iconBackgroundColorDark),
        brandingImage = a12.brandingImage.or(a12Base.brandingImage).resolveAgainst(projectDir),
        brandingImageDark = a12.brandingImageDark.or(a12Base.brandingImageDark).resolveAgainst(projectDir),
    )

    return ResolvedSplashSpec(
        variantName = variant.name,
        isDefaultVariant = isDefault,
        color = color,
        colorDark = colorDark,
        image = image,
        imageDark = imageDark,
        backgroundImage = backgroundImage,
        backgroundImageDark = backgroundImageDark,
        fullscreen = fullscreen,
        branding = branding,
        android = android,
        ios = ios,
        android12 = android12,
    )
}

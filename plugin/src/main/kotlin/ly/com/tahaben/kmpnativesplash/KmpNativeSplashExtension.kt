package ly.com.tahaben.kmpnativesplash

import ly.com.tahaben.kmpnativesplash.dsl.PlatformsBlock
import ly.com.tahaben.kmpnativesplash.dsl.SplashVariant
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Root DSL entry point — `kmpNativeSplash { … }`.
 *
 * Properties declared on the extension itself (color, image, branding, …) describe the
 * **default** variant. Per-flavor overrides go inside `flavor("dev") { … }`, and properties
 * left unset on a flavor are inherited from the default at resolution time.
 */
abstract class KmpNativeSplashExtension @Inject constructor(private val objects: ObjectFactory) {

    /**
     * Gradle project path of the standalone Android **application** module
     * (e.g. `":androidApp"`). AGP 9 split only; omit for the classic single-module
     * structure (auto-detected).
     *
     * Resolution precedence: this explicit override always wins; otherwise the single
     * module applying `com.android.application` is auto-detected; otherwise the applied
     * module is used (classic monolith / no-Android — byte-for-byte legacy behaviour).
     * The value is a Gradle project path, not a filesystem path.
     */
    var androidModule: String? = null

    /**
     * If true (default), and AGP is applied, the Android task writes into
     * `build/generated/kmpnativesplash/<variant>/res/` and registers it as a generated res dir.
     * If false (or AGP not applied), falls back to writing directly under `android.resRoot/<flavor>/res/`.
     */
    abstract val useGeneratedSourceSet: Property<Boolean>

    /** Platform on/off toggles. Both default to true. */
    val platforms: PlatformsBlock = objects.newInstance(PlatformsBlock::class.java).also {
        it.android.convention(true)
        it.ios.convention(true)
    }

    fun platforms(action: Action<PlatformsBlock>) {
        action.execute(platforms)
    }

    /** The base / default variant. Most projects only configure this. */
    val defaultVariant: SplashVariant = objects.newInstance(SplashVariant::class.java, "default")

    // Forward the variant's properties onto the extension so `kmpNativeSplash { color.set(...) }` works.
    val color get() = defaultVariant.color
    val colorDark get() = defaultVariant.colorDark
    val image get() = defaultVariant.image
    val imageDark get() = defaultVariant.imageDark
    val backgroundImage get() = defaultVariant.backgroundImage
    val backgroundImageDark get() = defaultVariant.backgroundImageDark
    val fullscreen get() = defaultVariant.fullscreen

    fun android(action: Action<ly.com.tahaben.kmpnativesplash.dsl.AndroidBlock>) =
        defaultVariant.android(action)

    fun ios(action: Action<ly.com.tahaben.kmpnativesplash.dsl.IosBlock>) =
        defaultVariant.ios(action)

    fun branding(action: Action<ly.com.tahaben.kmpnativesplash.dsl.BrandingBlock>) =
        defaultVariant.branding(action)

    fun android12(action: Action<ly.com.tahaben.kmpnativesplash.dsl.Android12Block>) =
        defaultVariant.android12(action)

    /** Additional flavors. Each entry is a delta over the default variant. */
    val flavors: NamedDomainObjectContainer<SplashVariant> =
        objects.domainObjectContainer(SplashVariant::class.java) { name ->
            objects.newInstance(SplashVariant::class.java, name)
        }

    /**
     * Declare or refine a flavor named [name]. Calling `flavor("dev") { … }` more than once
     * with the same name layers additional configuration onto the existing flavor instead of
     * recreating it.
     */
    fun flavor(name: String, action: Action<SplashVariant>) {
        action.execute(flavors.maybeCreate(name))
    }
}

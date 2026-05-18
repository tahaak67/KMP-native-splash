package ly.com.tahaben.kmpsplash.dsl

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * A single splash variant. The base extension uses one of these (name = "default");
 * additional named variants are declared via `flavor("dev") { … }` on the extension.
 *
 * All properties are optional — a variant declares only the fields it wants to override
 * relative to the base; the rest fall through to the base extension at resolution time.
 */
abstract class SplashVariant @Inject constructor(
    private val variantName: String,
    private val objects: ObjectFactory,
) : Named {
    override fun getName(): String = variantName

    // ---- Generic / shared with both platforms ----
    abstract val color: Property<String>
    abstract val colorDark: Property<String>
    abstract val image: Property<String>
    abstract val imageDark: Property<String>
    abstract val backgroundImage: Property<String>
    abstract val backgroundImageDark: Property<String>
    abstract val fullscreen: Property<Boolean>

    // ---- Nested blocks ----
    val androidBlock: AndroidBlock = objects.newInstance(AndroidBlock::class.java)
    val iosBlock: IosBlock = objects.newInstance(IosBlock::class.java)
    val brandingBlock: BrandingBlock = objects.newInstance(BrandingBlock::class.java)
    val android12Block: Android12Block = objects.newInstance(Android12Block::class.java)

    fun android(action: Action<AndroidBlock>) {
        action.execute(androidBlock)
    }

    fun ios(action: Action<IosBlock>) {
        action.execute(iosBlock)
    }

    fun branding(action: Action<BrandingBlock>) {
        action.execute(brandingBlock)
    }

    fun android12(action: Action<Android12Block>) {
        action.execute(android12Block)
    }
}

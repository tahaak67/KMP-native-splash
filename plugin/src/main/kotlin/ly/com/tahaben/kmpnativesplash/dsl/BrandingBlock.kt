package ly.com.tahaben.kmpnativesplash.dsl

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class BrandingPlatformBlock @Inject constructor(@Suppress("UNUSED_PARAMETER") objects: ObjectFactory) {
    abstract val image: Property<String>
    abstract val imageDark: Property<String>
    abstract val bottomPadding: Property<Int>
}

abstract class BrandingBlock @Inject constructor(private val objects: ObjectFactory) {
    abstract val image: Property<String>
    abstract val imageDark: Property<String>
    abstract val mode: Property<BrandingMode>
    abstract val bottomPadding: Property<Int>

    val android: BrandingPlatformBlock = objects.newInstance(BrandingPlatformBlock::class.java)
    val ios: BrandingPlatformBlock = objects.newInstance(BrandingPlatformBlock::class.java)

    fun android(action: Action<BrandingPlatformBlock>) {
        action.execute(android)
    }

    fun ios(action: Action<BrandingPlatformBlock>) {
        action.execute(ios)
    }
}

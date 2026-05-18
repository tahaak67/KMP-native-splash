package ly.com.tahaben.kmpnativesplash.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class PlatformsBlock @Inject constructor(@Suppress("UNUSED_PARAMETER") objects: ObjectFactory) {
    abstract val android: Property<Boolean>
    abstract val ios: Property<Boolean>
}

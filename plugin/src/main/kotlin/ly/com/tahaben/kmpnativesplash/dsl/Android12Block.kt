package ly.com.tahaben.kmpnativesplash.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Android 12+ splash screen (`androidx.core.splashscreen` / `windowSplashScreenAnimatedIcon`).
 */
abstract class Android12Block @Inject constructor(@Suppress("UNUSED_PARAMETER") objects: ObjectFactory) {
    abstract val color: Property<String>
    abstract val colorDark: Property<String>
    abstract val image: Property<String>
    abstract val imageDark: Property<String>
    abstract val iconBackgroundColor: Property<String>
    abstract val iconBackgroundColorDark: Property<String>
    abstract val brandingImage: Property<String>
    abstract val brandingImageDark: Property<String>
}

package ly.com.tahaben.kmpnativesplash.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class AndroidBlock @Inject constructor(@Suppress("UNUSED_PARAMETER") objects: ObjectFactory) {
    abstract val color: Property<String>
    abstract val colorDark: Property<String>
    abstract val image: Property<String>
    abstract val imageDark: Property<String>
    abstract val backgroundImage: Property<String>
    abstract val backgroundImageDark: Property<String>

    /** Maps to `android:gravity` on the bitmap. Defaults to `center`. */
    abstract val gravity: Property<String>

    /** Maps to `android:screenOrientation` on the activity. */
    abstract val screenOrientation: Property<String>

    /**
     * When true (default), the plugin sets `android:theme="@style/LaunchTheme"` on the
     * launcher activity in `AndroidManifest.xml`. Set to false if you want to manage the
     * activity theme yourself (for example, to keep a custom Material theme and instead
     * use `NativeSplash.installSplashScreen(this)` from the runtime library).
     *
     * Replacing an existing theme is logged as a warning so you can audit the change.
     */
    abstract val applyManifestTheme: Property<Boolean>

    /** Name of the splash theme written to `values/styles.xml` and applied to the activity. */
    abstract val themeName: Property<String>

    /**
     * Root for Android source-set directories. `<resRoot>/<flavor>/res/` is written into.
     * Relative paths resolve against **this module**'s directory; absolute paths are used
     * as-is. Defaults to `"src"`.
     */
    abstract val resRoot: Property<String>

    /**
     * Path to the `AndroidManifest.xml` to patch (`screenOrientation`, launcher activity
     * theme). Relative paths resolve against this module's directory; absolute paths are
     * used as-is. Defaults to `"src/androidMain/AndroidManifest.xml"`.
     */
    abstract val manifest: Property<String>
}

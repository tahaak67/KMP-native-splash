package ly.com.tahaben.kmpnativesplash.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class IosBlock @Inject constructor(@Suppress("UNUSED_PARAMETER") objects: ObjectFactory) {
    abstract val color: Property<String>
    abstract val colorDark: Property<String>
    abstract val image: Property<String>
    abstract val imageDark: Property<String>
    abstract val backgroundImage: Property<String>
    abstract val backgroundImageDark: Property<String>

    /** Maps to UIImageView `contentMode`. Defaults to `scaleAspectFit` for raster sources. */
    abstract val contentMode: Property<String>

    /**
     * Extra Info.plist paths (relative to the iosAppDir) to patch.
     * Defaults to `Info.plist` if empty.
     */
    abstract val infoPlistFiles: ListProperty<String>

    /**
     * Opt-in: auto-edit `project.pbxproj` to add per-flavor LAUNCH_SCREEN_STORYBOARD settings,
     * register the generated storyboards as project files, and switch Info.plist's
     * `UILaunchStoryboardName` to `$(LAUNCH_SCREEN_STORYBOARD)`. Defaults to false because
     * patching `.pbxproj` is risky if uncommitted Xcode changes are present.
     */
    abstract val autoWireXcodeFlavors: Property<Boolean>

    /**
     * Path to the iOS project directory containing the Xcode target, relative to **this
     * module**. Absolute paths are used as-is. Defaults to `"../iosApp"`, matching the stock
     * KMP layout (root contains `composeApp/`, `shared/`, `iosApp/`, …). Combined with
     * [targetName], drives the defaults for `iosAppDir` and the `.xcodeproj` path used by
     * Xcode auto-wiring.
     */
    abstract val projectPath: Property<String>

    /**
     * Name of the Xcode target / source folder inside [projectPath]. The plugin derives the
     * source root as `<projectPath>/<targetName>` and the project file as
     * `<projectPath>/<targetName>.xcodeproj`. Defaults to `"iosApp"`.
     */
    abstract val targetName: Property<String>

    /**
     * Root of the iOS app source folder (contains `Info.plist`, `Assets.xcassets`,
     * `Base.lproj/`). Relative paths resolve against this module's directory; absolute paths
     * are used as-is. Defaults to `<projectPath>/<targetName>` — override only if your
     * layout deviates.
     */
    abstract val appDir: Property<String>
}

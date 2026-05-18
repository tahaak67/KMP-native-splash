package ly.com.tahaben.kmpsplash

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.UIKit.UIApplication
import platform.UIKit.UIStoryboard
import platform.UIKit.UIView
import platform.UIKit.UIWindow

@OptIn(ExperimentalForeignApi::class)
actual object NativeSplash {

    private var overlay: UIView? = null
    private var preserved: Boolean = false
    private var explicitStoryboardName: String? = null

    actual fun preserve() {
        if (preserved) return
        preserved = true
        installOverlay()
    }

    actual fun remove() {
        if (!preserved) return
        preserved = false
        animateOutAndDetach()
    }

    /**
     * Storyboard used for the in-app preserve overlay.
     *
     * Default behavior (when not explicitly set) reads `UILaunchStoryboardName` from the
     * built app's Info.plist. Because the plugin writes `$(LAUNCH_SCREEN_STORYBOARD)` there
     * and Xcode substitutes the per-`XCBuildConfiguration` value at build time, this
     * resolves to the correct per-flavor storyboard (e.g. `LaunchScreenProduction`) without
     * any per-flavor runtime configuration. Falls back to `LaunchScreen` only when the plist
     * key is missing or unresolved.
     *
     * Assigning a value overrides the auto-detection — useful for custom storyboards that
     * aren't wired through `UILaunchStoryboardName`.
     */
    var storyboardName: String
        get() = explicitStoryboardName ?: launchStoryboardFromInfoPlist() ?: "LaunchScreen"
        set(value) {
            explicitStoryboardName = value
        }

    private fun launchStoryboardFromInfoPlist(): String? {
        val raw = NSBundle.mainBundle.objectForInfoDictionaryKey("UILaunchStoryboardName") as? String
        // Guard against build-setting substitution not running (would leave the literal
        // `$(LAUNCH_SCREEN_STORYBOARD)` token in the plist). Treat as unresolved.
        if (raw.isNullOrBlank() || raw.startsWith("$(")) return null
        return raw
    }

    private fun installOverlay() {
        if (overlay != null) return
        val window = keyWindow() ?: return
        val storyboard = runCatching {
            UIStoryboard.storyboardWithName(name = storyboardName, bundle = null)
        }.getOrNull() ?: return
        val vc = storyboard.instantiateInitialViewController() ?: return
        val view = vc.view
        view.setFrame(window.bounds)
        window.addSubview(view)
        window.bringSubviewToFront(view)
        overlay = view
    }

    private fun animateOutAndDetach() {
        val view = overlay ?: return
        overlay = null
        UIView.animateWithDuration(
            duration = 0.3,
            animations = { view.alpha = 0.0 },
            completion = { _ -> view.removeFromSuperview() },
        )
    }

    @Suppress("DEPRECATION")
    private fun keyWindow(): UIWindow? {
        val app = UIApplication.sharedApplication
        return app.keyWindow ?: app.windows.firstOrNull() as? UIWindow
    }
}


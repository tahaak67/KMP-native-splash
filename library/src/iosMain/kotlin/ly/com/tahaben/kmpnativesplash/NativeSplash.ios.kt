package ly.com.tahaben.kmpnativesplash

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
        if (!isHostedApp()) return
        if (preserved) return
        preserved = true
        installOverlay()
    }

    actual fun remove() {
        if (!isHostedApp()) return
        if (!preserved) return
        preserved = false
        animateOutAndDetach()
    }

    /**
     * True only inside a real hosted iOS app. A Kotlin/Native unit-test binary is a bare
     * `.kexe` run directly on the simulator — no `UIApplicationMain`, no app delegate, no
     * windows, and no app bundle, so `NSBundle.mainBundle.bundleIdentifier` is null. Any
     * UIKit access there (starting at `UIApplication.sharedApplication`) is a native
     * segfault, not a catchable error, so preserve/remove must be no-ops. Every shipping
     * iOS app/extension has a bundle identifier, so app behaviour is unaffected. This is
     * checked before any UIKit call and never touches UIKit itself.
     */
    private fun isHostedApp(): Boolean = NSBundle.mainBundle.bundleIdentifier != null

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


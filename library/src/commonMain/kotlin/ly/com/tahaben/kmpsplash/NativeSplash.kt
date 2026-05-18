package ly.com.tahaben.kmpsplash

import ly.com.tahaben.kmpsplash.NativeSplash.preserve
import ly.com.tahaben.kmpsplash.NativeSplash.remove


/**
 * Runtime entry point for keeping the native splash on screen past the OS-driven default.
 *
 * - **Android:** backed by AndroidX `SplashScreen`. Call [preserve] before the Activity's
 *   `super.onCreate()` and pair with `installSplashScreen(activity)` to defer the first frame.
 * - **iOS:** overlays the `LaunchScreen` storyboard on the key window until [remove] is called,
 *   then fades it out. Call [preserve] as early as possible (typically from your `AppDelegate`'s
 *   `application(_:didFinishLaunchingWithOptions:)` or the SwiftUI `@main` entry).
 *
 * Usage:
 * ```
 * // commonMain
 * NativeSplash.preserve()
 * // …load data, warm caches…
 * NativeSplash.remove()
 * ```
 *
 * JVM (incl. Compose Desktop), Linux, and web (`js` / `wasmJs`) targets ship no-op
 * actuals so the API is callable from multiplatform commonMain code without `expect`
 * checks.
 */
expect object NativeSplash {

    /** Keep the splash visible. Idempotent. */
    fun preserve()

    /** Dismiss the splash, animating out on iOS. Idempotent — calling before [preserve] is a no-op. */
    fun remove()
}

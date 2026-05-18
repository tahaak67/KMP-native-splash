package ly.com.tahaben.kmpnativesplash

// Aliased so the call below is unambiguous — without the alias, `installSplashScreen(activity)`
// inside `object NativeSplash` resolves to *our* member function and recurses forever.
import android.app.Activity
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import ly.com.tahaben.kmpnativesplash.NativeSplash.preserve
import ly.com.tahaben.kmpnativesplash.NativeSplash.remove
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen as androidxInstallSplashScreen

actual object NativeSplash {

    private val keep = AtomicBoolean(false)

    actual fun preserve() {
        keep.set(true)
    }

    actual fun remove() {
        keep.set(false)
    }

    /**
     * Wires AndroidX `installSplashScreen()` to honor [preserve] / [remove]. Call this from
     * your Activity's `onCreate()` **before** `super.onCreate()`:
     *
     * ```
     * override fun onCreate(savedInstanceState: Bundle?) {
     *     NativeSplash.installSplashScreen(this)
     *     super.onCreate(savedInstanceState)
     *     NativeSplash.preserve()
     *     lifecycleScope.launch {
     *         loadInitialData()
     *         NativeSplash.remove()
     *     }
     * }
     * ```
     *
     * The optional [onExit] callback receives the `SplashScreenViewProvider` once the platform
     * is about to dismiss the splash — useful for running a custom exit animation.
     */
    fun installSplashScreen(
        activity: Activity,
        onExit: ((SplashScreenViewProvider) -> Unit)? = null,
    ): SplashScreen {
        val splash = activity.androidxInstallSplashScreen()
        splash.setKeepOnScreenCondition { keep.get() }
        if (onExit != null) {
            splash.setOnExitAnimationListener { provider -> onExit(provider) }
        }
        return splash
    }
}

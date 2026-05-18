package ly.com.tahaben.kmpsplash

import kotlin.test.Test

class NativeSplashTest {

    @Test
    fun preserveAndRemoveAreCallable() {
        // The Android/iOS actuals have real side effects; on JVM/Linux test runtimes these
        // collapse to no-ops. We just guard that the API survives an unconditional call.
        NativeSplash.preserve()
        NativeSplash.remove()
        NativeSplash.remove() // double-remove is allowed.
        NativeSplash.preserve()
        NativeSplash.preserve() // double-preserve is allowed.
    }
}

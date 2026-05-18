package ly.com.tahaben.kmpsplash

/**
 * Web (Kotlin/JS) has no OS-driven native splash to preserve; the page's own loading
 * UI is the app's responsibility. The actual exists so multiplatform commonMain code
 * can call `NativeSplash.preserve()/.remove()` unconditionally.
 */
actual object NativeSplash {
    actual fun preserve() {}
    actual fun remove() {}
}

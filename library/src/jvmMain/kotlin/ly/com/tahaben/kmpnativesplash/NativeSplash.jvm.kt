package ly.com.tahaben.kmpnativesplash

/**
 * Pure-JVM target has no native splash to preserve. The actual exists so multiplatform
 * commonMain code can call `NativeSplash.preserve()/.remove()` unconditionally.
 */
actual object NativeSplash {
    actual fun preserve() {}
    actual fun remove() {}
}

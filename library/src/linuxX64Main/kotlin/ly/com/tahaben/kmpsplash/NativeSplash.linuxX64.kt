package ly.com.tahaben.kmpsplash

/** Linux has no concept of a native splash; preserve/remove are no-ops. */
actual object NativeSplash {
    actual fun preserve() {}
    actual fun remove() {}
}

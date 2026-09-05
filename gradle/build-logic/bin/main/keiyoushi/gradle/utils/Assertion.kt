package keiyoushi.gradle.utils

/**
 * Throws an [AssertionError] calculated by [lazyMessage] if the [value] is false.
 */
inline fun assertWithoutFlag(value: Boolean, lazyMessage: () -> Any) {
    if (!value) {
        val message = lazyMessage()
        throw AssertionError(message)
    }
}

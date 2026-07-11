package io.github.keiyoushi.gradle.internal

import io.github.keiyoushi.gradle.api.dsl.ExtensionDeeplink
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.kotlin.dsl.newInstance

internal fun addDeeplink(
    objects: ObjectFactory,
    deeplinks: ListProperty<ExtensionDeeplink>,
    block: ExtensionDeeplink.() -> Unit,
) {
    deeplinks.add(objects.newInstance<ExtensionDeeplink>().apply(block))
}

/**
 * Throws an [AssertionError] calculated by [lazyMessage] if the [value] is false.
 */
inline fun assertWithoutFlag(value: Boolean, lazyMessage: () -> Any) {
    if (!value) {
        val message = lazyMessage()
        throw AssertionError(message)
    }
}

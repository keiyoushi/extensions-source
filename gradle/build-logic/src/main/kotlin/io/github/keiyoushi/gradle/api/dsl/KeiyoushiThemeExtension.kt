package io.github.keiyoushi.gradle.api.dsl

import io.github.keiyoushi.gradle.internal.addDeeplink
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class KeiyoushiThemeExtension @Inject constructor(
    private val objects: ObjectFactory,
) {
    abstract val baseVersionCode: Property<Int>
    abstract val libVersion: Property<String>

    abstract val deeplinks: ListProperty<ExtensionDeeplink>

    fun deeplink(block: ExtensionDeeplink.() -> Unit) = addDeeplink(objects, deeplinks, block)
}

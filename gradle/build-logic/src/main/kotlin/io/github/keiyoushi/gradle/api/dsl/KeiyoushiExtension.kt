package io.github.keiyoushi.gradle.api.dsl

import io.github.keiyoushi.gradle.api.ContentWarning
import io.github.keiyoushi.gradle.internal.addDeeplink
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.newInstance
import javax.inject.Inject

abstract class KeiyoushiExtension @Inject constructor(
    private val objects: ObjectFactory,
) {
    abstract val name: Property<String>
    abstract val versionCode: Property<Int>
    abstract val contentWarning: Property<ContentWarning>
    abstract val theme: Property<String>
    abstract val libVersion: Property<String>
    abstract val pkgName: Property<String>

    abstract val deeplinks: ListProperty<ExtensionDeeplink>
    abstract val sources: ListProperty<ExtensionSource>

    /** Set only by the Keiyoushi Runtime extension, see [runtime]. */
    abstract val runtimeHost: Property<RuntimeHostSpec>

    fun deeplink(block: ExtensionDeeplink.() -> Unit) = addDeeplink(objects, deeplinks, block)

    /**
     * Makes this module the Keiyoushi Runtime. Its entry point is a generated factory with no sources
     * that registers the modules it [RuntimeHostSpec.provides] for other extensions.
     */
    fun runtime(block: RuntimeHostSpec.() -> Unit) {
        runtimeHost.set(objects.newInstance<RuntimeHostSpec>().apply(block))
    }

    fun source(block: ExtensionSource.() -> Unit) {
        sources.add(objects.newInstance<ExtensionSource>().apply(block))
    }
}

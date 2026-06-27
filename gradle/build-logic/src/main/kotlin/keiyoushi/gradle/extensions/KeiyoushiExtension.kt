package keiyoushi.gradle.extensions

import ContentWarning
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.Serializable
import javax.inject.Inject

val VALID_LIB_VERSIONS = listOf("1.4")

data class DeeplinkFilter(
    val hosts: List<String>,
    val pathPatterns: List<String>,
) : Serializable

abstract class DeeplinkSpec {
    abstract val hosts: ListProperty<String>
    abstract val pathPatterns: ListProperty<String>

    fun host(host: String) {
        hosts.add(host)
    }

    fun path(pattern: String) {
        pathPatterns.add(pattern)
    }
}

abstract class KeiyoushiExtension @Inject constructor(
    private val objects: ObjectFactory,
) {
    abstract val name: Property<String>
    abstract val className: Property<String>
    abstract val versionCode: Property<Int>
    abstract val contentWarning: Property<ContentWarning>
    abstract val theme: Property<String>
    abstract val libVersion: Property<String>
    abstract val baseUrl: Property<String>

    abstract val deeplinks: ListProperty<DeeplinkSpec>

    fun deeplink(block: DeeplinkSpec.() -> Unit) = addDeeplink(objects, deeplinks, block)
}

abstract class KeiyoushiMultisrcExtension @Inject constructor(
    private val objects: ObjectFactory,
) {
    abstract val baseVersionCode: Property<Int>
    abstract val libVersion: Property<String>

    abstract val deeplinks: ListProperty<DeeplinkSpec>

    fun deeplink(block: DeeplinkSpec.() -> Unit) = addDeeplink(objects, deeplinks, block)
}

private fun addDeeplink(
    objects: ObjectFactory,
    deeplinks: ListProperty<DeeplinkSpec>,
    block: DeeplinkSpec.() -> Unit,
) {
    deeplinks.add(objects.newInstance(DeeplinkSpec::class.java).apply(block))
}

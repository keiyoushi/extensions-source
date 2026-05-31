package keiyoushi.gradle.extension.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class DeeplinkSpec {
    abstract val scheme: Property<String>
    abstract val host: Property<String>
    abstract val pathPatterns: ListProperty<String>

    fun path(pattern: String) {
        pathPatterns.add(pattern)
    }
}

abstract class DeeplinksSpec @Inject constructor(
    private val objects: ObjectFactory,
) {
    internal abstract val specs: ListProperty<DeeplinkSpec>

    fun deeplink(block: DeeplinkSpec.() -> Unit) {
        val d = objects.newInstance(DeeplinkSpec::class.java)
        d.block()
        specs.add(d)
    }
}

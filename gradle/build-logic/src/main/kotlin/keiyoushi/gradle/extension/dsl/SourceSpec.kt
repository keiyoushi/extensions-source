package keiyoushi.gradle.extension.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class SourceSpec @Inject constructor(
    private val objects: ObjectFactory,
) {
    abstract val name: Property<String>
    abstract val lang: Property<String>
    abstract val versionId: Property<Int>
    abstract val id: Property<Long>
    internal abstract val resolvedBaseUrl: Property<BaseUrlSpec>
    abstract val overrides: MapProperty<String, OverrideValue>
    internal abstract val deeplinkSpec: Property<DeeplinkSpec>

    fun baseUrl(url: String) {
        resolvedBaseUrl.set(BaseUrlSpec.Static(url))
    }

    fun baseUrl(spec: BaseUrlSpec) {
        resolvedBaseUrl.set(spec)
    }

    fun override(name: String, value: String) {
        overrides.put(name, OverrideValue.Str(value))
    }

    fun override(name: String, value: Int) {
        overrides.put(name, OverrideValue.IntV(value))
    }

    fun override(name: String, value: Long) {
        overrides.put(name, OverrideValue.LongV(value))
    }

    fun override(name: String, value: Boolean) {
        overrides.put(name, OverrideValue.BoolV(value))
    }

    fun deeplink(block: DeeplinkSpec.() -> Unit) {
        val d = objects.newInstance(DeeplinkSpec::class.java)
        d.block()
        deeplinkSpec.set(d)
    }
}

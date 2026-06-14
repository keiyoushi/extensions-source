package keiyoushi.gradle.extension.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

@DslMarker
annotation class KeiyoushiDsl

@KeiyoushiDsl
class OverridesBuilder(
    private val overrides: MapProperty<String, OverrideValue>,
) {
    infix fun String.to(value: Int) {
        overrides.put(this, OverrideValue.IntV(value))
    }

    infix fun String.to(value: Long) {
        overrides.put(this, OverrideValue.LongV(value))
    }

    infix fun String.to(value: Boolean) {
        overrides.put(this, OverrideValue.BoolV(value))
    }

    infix fun String.to(value: String) {
        overrides.put(this, OverrideValue.Str(value))
    }
}

@KeiyoushiDsl
abstract class SourceSpec @Inject constructor(
    private val objects: ObjectFactory,
) {
    abstract val name: Property<String>
    abstract val lang: Property<String>
    abstract val configurable: Property<Boolean>
    abstract val versionId: Property<Int>
    abstract val id: Property<Long>

    internal abstract val resolvedBaseUrl: Property<BaseUrlSpec>
    internal abstract val overrides: MapProperty<String, OverrideValue>
    internal abstract val specs: ListProperty<DeeplinkSpec>

    fun baseUrl(url: String) {
        resolvedBaseUrl.set(BaseUrlSpec.Static(url))
    }

    fun baseUrl(block: BaseUrlDsl.() -> Unit) {
        val dsl = objects.newInstance(BaseUrlDsl::class.java)
        dsl.block()
        resolvedBaseUrl.set(dsl.build())
    }

    fun overrides(block: OverridesBuilder.() -> Unit) {
        OverridesBuilder(overrides).apply(block)
    }

    fun deeplink(block: DeeplinkSpec.() -> Unit) {
        val d = objects.newInstance(DeeplinkSpec::class.java)
        d.block()
        specs.add(d)
    }
}

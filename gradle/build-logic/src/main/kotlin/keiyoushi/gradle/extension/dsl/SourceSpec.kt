package keiyoushi.gradle.extension.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

@DslMarker
annotation class OverridesDsl

@OverridesDsl
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

abstract class SourceSpec @Inject constructor(
    private val objects: ObjectFactory,
) {
    abstract val name: Property<String>
    abstract val lang: Property<String>
    abstract val contentRating: Property<ContentRating>
    abstract val configurableSource: Property<Boolean>
    abstract val versionId: Property<Int>
    abstract val id: Property<Long>

    internal abstract val resolvedBaseUrl: Property<BaseUrlSpec>
    internal abstract val overrides: MapProperty<String, OverrideValue>
    internal abstract val deeplinks: MapProperty<String, Any> // Placeholder for future expansion if needed, but for now we use specs
    internal abstract val specs: org.gradle.api.provider.ListProperty<DeeplinkSpec>

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

    fun deeplinks(block: DeeplinksSpec.() -> Unit) {
        val d = objects.newInstance(DeeplinksSpec::class.java)
        d.block()
        specs.addAll(d.specs)
    }
}

package keiyoushi.gradle.extension.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

@KeiyoushiDsl
abstract class ExtensionSpec @Inject constructor(
    private val objects: ObjectFactory,
) {
    abstract val name: Property<String>
    abstract val className: Property<String>
    abstract val versionCode: Property<Int>
    abstract val nsfw: Property<Boolean>
    abstract val theme: Property<String>
    internal abstract val sources: ListProperty<SourceSpec>

    fun source(block: SourceSpec.() -> Unit) {
        val s = objects.newInstance(SourceSpec::class.java)
        s.block()
        sources.add(s)
    }
}

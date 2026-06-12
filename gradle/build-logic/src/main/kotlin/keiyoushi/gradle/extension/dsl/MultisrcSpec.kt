package keiyoushi.gradle.extension.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

@KeiyoushiDsl
abstract class MultisrcSpec @Inject constructor(
    private val objects: ObjectFactory,
) {
    abstract val baseVersionCode: Property<Int>
    internal abstract val deeplinks: ListProperty<DeeplinkSpec>

    fun deeplink(block: DeeplinkSpec.() -> Unit) {
        val d = objects.newInstance(DeeplinkSpec::class.java)
        d.block()
        deeplinks.add(d)
    }
}

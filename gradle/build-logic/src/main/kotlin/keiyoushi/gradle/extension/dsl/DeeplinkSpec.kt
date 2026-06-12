package keiyoushi.gradle.extension.dsl

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

@KeiyoushiDsl
abstract class DeeplinkSpec {
    abstract val scheme: Property<String>
    abstract val host: Property<String>
    abstract val pathPatterns: ListProperty<String>

    fun path(pattern: String) {
        pathPatterns.add(pattern)
    }
}

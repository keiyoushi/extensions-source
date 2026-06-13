package keiyoushi.gradle.extension.dsl

import org.gradle.api.provider.ListProperty

@KeiyoushiDsl
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

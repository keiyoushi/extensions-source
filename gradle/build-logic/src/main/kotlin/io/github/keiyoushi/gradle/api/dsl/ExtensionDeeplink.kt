package io.github.keiyoushi.gradle.api.dsl

import org.gradle.api.provider.ListProperty

abstract class ExtensionDeeplink {
    abstract val hosts: ListProperty<String>
    abstract val pathPatterns: ListProperty<String>

    fun host(host: String) {
        hosts.add(host)
    }

    fun path(pattern: String) {
        pathPatterns.add(pattern)
    }
}

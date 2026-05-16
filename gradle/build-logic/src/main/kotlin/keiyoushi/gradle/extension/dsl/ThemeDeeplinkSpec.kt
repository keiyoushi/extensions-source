package keiyoushi.gradle.extension.dsl

import org.gradle.api.provider.ListProperty

abstract class ThemeDeeplinkSpec {
    abstract val pathPatterns: ListProperty<String>

    fun path(pattern: String) {
        pathPatterns.add(pattern)
    }
}

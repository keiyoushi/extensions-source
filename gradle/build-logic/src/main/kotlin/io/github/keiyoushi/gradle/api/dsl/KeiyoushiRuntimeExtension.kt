package io.github.keiyoushi.gradle.api.dsl

import io.github.keiyoushi.gradle.internal.checkRuntimeModule
import org.gradle.api.provider.MapProperty

/**
 * Keiyoushi Runtime modules this lib, theme or extension calls. Each one becomes a
 * `RuntimeModules.<module>` property in the project's namespace, and the extensions built with it
 * list the modules in the repo index.
 */
abstract class KeiyoushiRuntimeExtension {
    abstract val requires: MapProperty<String, Int>

    /** Calls [module]'s `Entry` methods up to API level [api]. */
    fun requires(module: String, api: Int) {
        checkRuntimeModule(module, api)
        requires.put(module, api)
    }
}

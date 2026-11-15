package io.github.keiyoushi.gradle.api.dsl

import io.github.keiyoushi.gradle.internal.checkRuntimeModule
import org.gradle.api.provider.MapProperty

/** The modules the Keiyoushi Runtime extension serves, each with its current API level. */
abstract class RuntimeHostSpec {
    abstract val provides: MapProperty<String, Int>
    abstract val minApis: MapProperty<String, Int>

    /**
     * Serves `keiyoushi.runtime.<module>.Entry` at API level [api]. Extensions built for a level
     * below [minApi] are told to update instead.
     */
    fun provides(module: String, api: Int, minApi: Int = 1) {
        checkRuntimeModule(module, api)
        check(minApi in 1..api) { "Runtime module '$module': minApi must be between 1 and $api, got $minApi" }
        provides.put(module, api)
        minApis.put(module, minApi)
    }
}

package keiyoushi.lib.runtime

import java.util.Collections

/**
 * What the runtime publishes in [System.getProperties] under [KEY]. Extensions built at different
 * times each carry their own copy of this lib, so only app-loaded types go in it. Consumers ignore
 * keys they don't know, so new keys can be added freely. A layout that old consumers can't read
 * goes under a new key, with this one still published.
 */
internal object Registration {
    const val KEY = "keiyoushi.runtime.v1"

    const val VERSION_CODE = "versionCode"
    const val VERSION_NAME = "versionName"

    /** `Map<String, Int>`: module to the API level it serves */
    const val APIS = "apis"

    /** `Map<String, Int>`: module to the lowest API level it still serves */
    const val MIN_APIS = "minApis"

    /** `Map<String, Any>`: module to its `Entry` object */
    const val ENTRIES = "entries"

    fun current(): Map<*, *>? = System.getProperties()[KEY] as? Map<*, *>

    /** Publishes [registration] unless a runtime at least as new is already registered */
    fun publish(versionCode: Int, registration: Map<String, Any>): Boolean {
        val properties = System.getProperties()
        synchronized(properties) {
            val current = (properties[KEY] as? Map<*, *>)?.get(VERSION_CODE) as? Int
            if (current != null && current >= versionCode) return false
            properties[KEY] = Collections.unmodifiableMap(registration)
            return true
        }
    }
}

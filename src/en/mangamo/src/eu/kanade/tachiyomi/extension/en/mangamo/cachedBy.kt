package eu.kanade.tachiyomi.extension.en.mangamo

import kotlin.reflect.KProperty

@Suppress("ClassName")
class cachedBy<T>(private val dependencies: () -> Any?, private val callback: () -> T) {
    private object UNINITIALIZED
    private var cachedValue: Any? = UNINITIALIZED
    private var lastDeps: Any? = UNINITIALIZED

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        synchronized(this) {
            val newDeps = dependencies()
            if (newDeps != lastDeps) {
                lastDeps = newDeps
                cachedValue = callback()
            }
            return cachedValue as T
        }
    }
}

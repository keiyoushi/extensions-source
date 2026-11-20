package eu.kanade.tachiyomi.multisrc.zingtheme

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Small deployment-scoped values; obsolete requests cannot replace the chosen origin's state. */
class OriginCache<T>(private val currentOrigin: () -> String) {
    private val mutex = Mutex()
    private var cachedOrigin: String? = null
    private val values = mutableMapOf<String, T>()

    suspend fun get(origin: String, key: String, refresh: Boolean = false, load: suspend () -> T): T = mutex.withLock {
        if (currentOrigin() != origin) return@withLock load()
        if (cachedOrigin != origin) {
            values.clear()
            cachedOrigin = origin
        }
        if (!refresh) values[key]?.let { return@withLock it }
        val value = load()
        if (currentOrigin() == origin) values[key] = value
        value
    }

    suspend fun invalidate(origin: String, key: String) = mutex.withLock {
        if (cachedOrigin == origin) values.remove(key)
        Unit
    }
}

package eu.kanade.tachiyomi.extension.all.pixiv

import android.util.LruCache

internal fun countUp(start: Int = 0) = sequence<Int> {
    yieldAll(start..Int.MAX_VALUE)
    throw RuntimeException("Overflow")
}

/**
 * Lazily fetches pages through [fetchPage] (which returns null once there are no more pages)
 * and hands out results in chunks of arbitrary size.
 */
internal class PagedBuffer<T>(private val fetchPage: suspend (page: Int) -> List<T>?) {
    private var nextPage = 1
    private var exhausted = false
    private val buffer = ArrayDeque<T>()

    suspend fun take(count: Int): List<T> {
        while (buffer.size < count && !exhausted) {
            val items = fetchPage(nextPage++)
            if (items == null) exhausted = true else buffer.addAll(items)
        }

        return List(minOf(count, buffer.size)) { buffer.removeFirst() }
    }
}

internal fun <K : Any, V : Any> lruCached(capacity: Int, compute: suspend (K) -> V?): suspend (K) -> V? {
    val cache = LruCache<K, V>(capacity)

    return { key -> cache.get(key) ?: compute(key)?.also { cache.put(key, it) } }
}

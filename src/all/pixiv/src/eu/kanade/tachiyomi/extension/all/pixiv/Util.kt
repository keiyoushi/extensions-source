package eu.kanade.tachiyomi.extension.all.pixiv

import android.util.LruCache

internal fun countUp(start: Int = 0) = sequence<Int> {
    yieldAll(start..Int.MAX_VALUE)
    throw RuntimeException("Overflow")
}

internal fun <T> Iterator<T>.truncateToList(count: Int): List<T> = buildList {
    repeat(count) {
        if (!hasNext()) return@buildList
        add(next())
    }
}

internal fun parseSMangaUrl(url: String): Pair<String, Boolean> {
    val isSeries = url.getOrNull(1) != 'a'
    return Pair(url.substringAfterLast('/'), isSeries)
}

internal fun <K, V> lruCached(capacity: Int, compute: (K) -> V): (K) -> V {
    val cache = object : LruCache<K, V>(capacity) {
        override fun create(key: K): V = compute(key)
    }

    return { key -> synchronized(cache) { cache.get(key) } }
}

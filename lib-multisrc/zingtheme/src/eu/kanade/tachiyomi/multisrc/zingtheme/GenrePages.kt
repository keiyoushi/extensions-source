package eu.kanade.tachiyomi.multisrc.zingtheme

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal suspend fun mergePages(
    genres: List<String>,
    page: Int,
    fetch: suspend (String, Int) -> MangasPage,
): MangasPage {
    val lists = coroutineScope {
        genres.map { genre -> async { fetch(genre, page) } }.map { it.await() }
    }
    val merged = LinkedHashMap<String, SManga>()
    for (index in 0 until (lists.maxOfOrNull { it.mangas.size } ?: 0)) {
        lists.forEach { result ->
            result.mangas.getOrNull(index)?.let { merged.putIfAbsent(it.url, it) }
        }
    }
    return MangasPage(merged.values.toList(), lists.any { it.hasNextPage })
}

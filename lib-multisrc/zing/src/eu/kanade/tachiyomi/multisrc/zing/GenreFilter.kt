package eu.kanade.tachiyomi.multisrc.zing

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(private val genres: List<Pair<String, String>>) : Filter.Select<String>("ジャンル", (listOf("すべて") + genres.map { it.first }).toTypedArray()) {
    val value get() = genres.getOrNull(state - 1)?.second
}

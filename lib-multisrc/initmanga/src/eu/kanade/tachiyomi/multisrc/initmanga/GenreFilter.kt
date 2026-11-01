package eu.kanade.tachiyomi.multisrc.initmanga

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(name: String, private val genres: List<Pair<String, String?>>) : Filter.Select<String>(name, genres.map { it.first }.toTypedArray()) {
    val path get() = genres.getOrNull(state)?.second
}

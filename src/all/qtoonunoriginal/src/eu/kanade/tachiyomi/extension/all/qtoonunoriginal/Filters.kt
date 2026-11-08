package eu.kanade.tachiyomi.extension.all.qtoonunoriginal

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter : Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second
}

class GenreFilter(private val genres: List<Pair<String, String>>) : Filter.Select<String>("Genre", genres.map { it.first }.toTypedArray()) {
    val value get() = genres[state].second.ifEmpty { null }
}

private val SORTS = listOf(
    "Latest" to "latest",
    "Most popular" to "popular",
    "Top rated" to "rating",
    "A-Z" to "az",
)

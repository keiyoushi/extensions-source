package eu.kanade.tachiyomi.extension.en.manhwa210

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class GenreOption(val name: String, val id: String)

class SortFilter : Filter.Select<String>("Sort", SORTS.map { it.first }.toTypedArray(), 3) {
    val value get() = SORTS[state].second
}

class SearchTypeFilter : Filter.Select<String>("Search by", SEARCH_TYPES.map { it.first }.toTypedArray()) {
    val value get() = SEARCH_TYPES[state].second
}

class Genre(name: String, val id: String) : Filter.TriState(name)

class GenreFilter(genres: List<GenreOption>) : Filter.Group<Genre>("Genres", genres.map { Genre(it.name, it.id) })

private val SORTS = listOf(
    "Update" to "-updated_at",
    "Newest" to "-created_at",
    "Oldest" to "created_at",
    "Popular" to "-views",
    "A-Z" to "name",
    "Z-A" to "-name",
)

private val SEARCH_TYPES = listOf(
    "Name" to "name",
    "Artist" to "artist",
    "Doujinshi" to "doujinshi",
)

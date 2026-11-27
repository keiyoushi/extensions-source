package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

class TypeFilter : Filter.Select<String>("Type", arrayOf("Manga", "Original")) {
    val isOriginal get() = state == 1
}

class SortFilter : Filter.Select<String>("Sort (Original)", SORTS.map { it.first }.toTypedArray()) {
    val value get() = SORTS[state].second

    private companion object {
        val SORTS = listOf("Updated" to "updated", "Views" to "views", "Rating" to "rating")
    }
}

class AuthorFilter : Filter.Text("Author")

class ArtistFilter : Filter.Text("Artist")

class SelectFilter(name: String, val parameter: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class GenreFilter(genres: List<Pair<String, String>>) : Filter.Group<GenreOption>("Genres", genres.map { GenreOption(it.first, it.second) }) {
    val included get() = state.filter { it.isIncluded() }.map { it.id }
    val excluded get() = state.filter { it.isExcluded() }.map { it.id }
}

class GenreOption(name: String, val id: String) : Filter.TriState(name)

@Serializable
class FilterData(
    val genres: List<Pair<String, String>>,
    val years: List<Pair<String, String>>,
    val ratings: List<Pair<String, String>>,
)

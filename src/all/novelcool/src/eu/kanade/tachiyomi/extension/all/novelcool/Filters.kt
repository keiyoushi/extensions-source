package eu.kanade.tachiyomi.extension.all.novelcool

import eu.kanade.tachiyomi.source.model.Filter

class AuthorFilter(title: String) : Filter.Text(title)

class GenreFilter(title: String, genres: List<Pair<String, String>>) : Filter.Group<Genre>(title, genres.map { Genre(it.first, it.second) }) {
    val included: List<String>
        get() = state.filter { it.isIncluded() }.map { it.id }

    val excluded: List<String>
        get() = state.filter { it.isExcluded() }.map { it.id }
}

class Genre(name: String, val id: String) : Filter.TriState(name)

internal fun getStatusList() = listOf(
    Pair("All", ""),
    Pair("Completed", "YES"),
    Pair("Ongoing", "NO"),
)

class StatusFilter(title: String, private val status: List<Pair<String, String>>) : Filter.Select<String>(title, status.map { it.first }.toTypedArray()) {
    fun getValue() = status[state].second
}

internal fun getRatingList() = listOf(
    Pair("All", ""),
    Pair("5 Star", "5"),
    Pair("4 Star", "4"),
    Pair("3 Star", "3"),
    Pair("2 Star", "2"),
)

class RatingFilter(title: String, private val ratings: List<Pair<String, String>>) : Filter.Select<String>(title, ratings.map { it.first }.toTypedArray()) {
    fun getValue() = ratings[state].second
}

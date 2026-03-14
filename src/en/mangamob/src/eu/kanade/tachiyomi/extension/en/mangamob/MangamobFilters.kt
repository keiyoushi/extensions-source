package eu.kanade.tachiyomi.extension.en.mangamob

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal fun getMangamobFilters(): FilterList = FilterList(
    Filter.Header("NOTE: Ignored when using text search"),
    Filter.Separator(),
    SortFilter("Sort by"),
    Filter.Separator(),
    GenreFilter(),
)

internal class SortFilter(name: String, private val options: Array<Pair<String, String>> = sortFilters, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val selected get() = options[state].second
}

internal class GenreFilter :
    Filter.Group<Genre>(
        "Genre",
        genres.map(::Genre),
    )

internal class Genre(name: String) : Filter.CheckBox(name)

private val sortFilters = arrayOf(
    "Random" to "Random",
    "Latest Updated" to "Updated",
    "New" to "New",
    "Most Viewed" to "Views",
)

private val genres = listOf(
    "Action",
    "Adventure",
    "Comedy",
    "Cooking",
    "Manga",
    "Drama",
    "Fantasy",
    "Gender bender",
    "Harem",
    "Historical",
    "Horror",
    "Isekai",
    "Josei",
    "Manhua",
    "Manhwa",
    "Martial arts",
    "Mature",
    "Mecha",
    "Medical",
    "Mystery",
    "One shot",
    "Psychological",
    "Romance",
    "School life",
    "Sci fi",
    "Seinen",
    "Shoujo",
    "Shounen",
    "Slice of life",
    "Sports",
    "Supernatural",
    "Tragedy",
    "Webtoons",
    "ladies",
)

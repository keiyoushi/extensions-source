package eu.kanade.tachiyomi.extension.en.mangarawclub

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SelectFilter("Sort by", getSortsList),
        GenreFilter("Genre", getGenres),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TextFilter("Tags"),
        Filter.Separator(),
        ChapterFilter("Minimum Chapter"),
    )
}

internal class GenreFilter(name: String, genreList: List<String>) :
    Filter.Group<TriFilter>(name, genreList.map { TriFilter(it) })

internal open class TriFilter(name: String) : Filter.TriState(name)

internal open class ChapterFilter(name: String) : Filter.Text(name)

internal open class TextFilter(name: String) : Filter.Text(name)

internal open class SelectFilter(name: String, val vals: List<String>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it }.toTypedArray(), state)

private val getGenres = listOf(
    "R-18",
    "Action",
    "Adult",
    "Adventure",
    "Comedy",
    "Cooking",
    "Doujinshi",
    "Drama",
    "Ecchi",
    "Fantasy",
    "Gender bender",
    "Harem",
    "Historical",
    "Horror",
    "Isekai",
    "Josei",
    "Ladies",
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
)

private val getSortsList = listOf(
    "Random",
    "New",
    "Updated",
    "Views",
)

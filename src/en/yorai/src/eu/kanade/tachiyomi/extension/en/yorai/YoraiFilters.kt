package eu.kanade.tachiyomi.extension.en.yorai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SortFilter("Sort by", Filter.Sort.Selection(0, false), getSortsList),
        StatusFilter("Status", getStatusList),
        TypeFilter("Types", getTypeList),
        GenreFilter("Genre", getGenres),
    )
}
internal open class StatusFilter(name: String, val vals: List<String>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it }.toTypedArray(), state) {
    val selected get() = vals[state].replace("All Status", "")
}

internal open class TypeFilter(name: String, val vals: List<String>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it }.toTypedArray(), state) {
    val selected get() = vals[state].replace("All Types", "")
}

internal class GenreFilter(name: String, genreList: List<String>) :
    Filter.Group<CheckBoxFilter>(name, genreList.map { CheckBoxFilter(it) })

internal open class CheckBoxFilter(name: String) : Filter.CheckBox(name)
internal open class TriFilter(name: String) : Filter.TriState(name)

internal open class TextFilter(name: String) : Filter.Text(name)

internal open class SortFilter(name: String, selection: Selection, private val vals: List<Pair<String, String>>) :
    Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
    val selected get() = vals[state!!.index].second
}
private val getStatusList = listOf(
    "All Status",
    "Ongoing",
    "Completed",
    "Hiatus",
)

private val getGenres = listOf(
    "Action",
    "Adventure",
    "Fantasy",
    "Comedy",
    "Historical",
    "Martial Arts",
    "Seinen",
    "Shounen",
    "Supernatural",
    "Drama",
    "Harem",
    "School Life",
    "Mature",
    "Horror",
    "Psychological",
    "Romance",
    "Mystery",
    "Thriller",
    "Tragedy",
    "Award Winning",
    "Sci Fi",
    "Josei",
    "Ecchi",
    "Slice Of Life",
    "Sports",
    "Suspense",
    "Gourmet",
)
private val getTypeList = listOf(
    "All Types",
    "Manga",
    "Manhwa",
    "Manhua",
    "Webtoon",
)

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Recently Updated", "updated_at"),
    Pair("Alphabetical", "title"),
    Pair("Most Chapters", "chapter_count"),
)

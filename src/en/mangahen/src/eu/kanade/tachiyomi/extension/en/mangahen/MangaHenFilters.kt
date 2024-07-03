package eu.kanade.tachiyomi.extension.en.mangahen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SortFilter("Sort by", getSortsList),
        TypeFilter("Types", getTypesList),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TextFilter("Tags"),
    )
}

internal open class TextFilter(name: String) : Filter.Text(name)

internal open class SortFilter(name: String, vals: List<Pair<String, String>>) : SelectFilter(name, vals)

internal open class TypeFilter(name: String, vals: List<Pair<String, String>>) : SelectFilter(name, vals)

internal open class SelectFilter(name: String, private val vals: List<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getValue() = vals[state].second
}

private val getTypesList: List<Pair<String, String>> = listOf(
    Pair("All", "0"),
    Pair("Manga", "1"),
    Pair("Doujinshi", "2"),
)

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Newest", "2"),
    Pair("Popular", "1"),
    Pair("Relevance", "0"),
    Pair("Best Rated", "3"),
    Pair("Most Viewed", "4"),
)

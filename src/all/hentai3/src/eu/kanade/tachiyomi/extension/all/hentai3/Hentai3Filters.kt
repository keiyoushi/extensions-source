package eu.kanade.tachiyomi.extension.all.hentai3

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SelectFilter("Sort by", getSortsList),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        Filter.Header("Use 'Male Tags' or 'Female Tags' for specific categories. 'Tags' searches all categories."),
        TextFilter("Tags", "tags"),
        TextFilter("Male Tags", "tags", "male"),
        TextFilter("Female Tags", "tags", "female"),
        TextFilter("Series", "series"),
        TextFilter("Characters", "characters"),
        TextFilter("Artists", "artist"),
        TextFilter("Groups", "groups"),
        TextFilter("Languages", "language"),
        Filter.Separator(),
        Filter.Header("Filter by pages, for example: (>20)"),
        TextFilter("Pages", "page"),
    )
}

internal open class TextFilter(name: String, val type: String, val specific: String = "") : Filter.Text(name)

internal open class SelectFilter(name: String, private val vals: List<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getValue() = vals[state].second
}

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Recent", ""),
    Pair("Popular: All Time", "popular"),
    Pair("Popular: Week", "popular-7d"),
    Pair("Popular: Today", "popular-24h"),
)

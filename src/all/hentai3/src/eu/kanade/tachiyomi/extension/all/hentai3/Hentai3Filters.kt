package eu.kanade.tachiyomi.extension.all.hentai3

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(showLanguage: Boolean): FilterList = FilterList(
    listOfNotNull(
        Filter.Header("Add quote (\"...\") for exact search query match"),
        SelectFilter("Sort by", getSortsList),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        Filter.Header("Use 'Male Tags' or 'Female Tags' for specific categories. 'Tags' searches all other categories."),
        TextFilter("Tags", "tag"),
        TextFilter("Male Tags", "tag", "male"),
        TextFilter("Female Tags", "tag", "female"),
        TextFilter("Series", "series"),
        TextFilter("Characters", "character"),
        TextFilter("Artists", "artist"),
        TextFilter("Groups", "group"),
        if (showLanguage) TextFilter("Languages", "language") else null,
        TextFilter("Categories", "category"),
        Filter.Header("Uploaded valid units are h, d, w, m, y."),
        Filter.Header("example: >20d or <20d"),
        TextFilter("Uploaded", "added"),
        Filter.Separator(),
        Filter.Header("Filter by pages, for example: 20 or >20 or <20"),
        TextFilter("Pages", "pages"),
        OffsetPageFilter(),
        FavoriteFilter(),
        Filter.Separator(),
        Filter.Header("Search manga directly with `id:<code>`"),
    ),
)

internal open class TextFilter(name: String, val type: String, val specific: String = "") : Filter.Text(name)

class OffsetPageFilter : Filter.Text("Offset results by # pages")

internal class FavoriteFilter : Filter.CheckBox("Favorites only", false)

internal open class SelectFilter(name: String, private val vals: List<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getValue() = vals[state].second
}

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Recent", ""),
    Pair("Popular: All Time", "popular"),
    Pair("Popular: Week", "popular-7d"),
    Pair("Popular: Today", "popular-24h"),
)

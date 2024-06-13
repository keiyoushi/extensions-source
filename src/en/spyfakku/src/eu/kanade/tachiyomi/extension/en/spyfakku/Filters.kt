package eu.kanade.tachiyomi.extension.en.spyfakku

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Filter.Sort.Selection
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SortFilter("Sort by", Selection(0, false), getSortsList),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TextFilter("Tags", "tag"),
        TextFilter("Artists", "artist"),
        TextFilter("Magazines", "magazine"),
        TextFilter("Parodies", "parody"),
        TextFilter("Circles", "circle"),
        TextFilter("Pages", "pages"),
    )
}

internal open class TextFilter(name: String, val type: String) : Filter.Text(name)
internal open class SortFilter(name: String, selection: Selection, private val vals: List<Pair<String, String>>) :
    Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
    fun getValue() = vals[state!!.index].second
}

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("ID", "id"),
    Pair("Title", "title"),
    Pair("Created", "created_at"),
    Pair("Published", "published_at"),
    Pair("Pages", "pages"),
)

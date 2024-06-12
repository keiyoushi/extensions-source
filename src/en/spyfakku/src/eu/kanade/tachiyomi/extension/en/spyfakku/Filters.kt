package eu.kanade.tachiyomi.extension.en.spyfakku

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SortFilter("Sort by", getSortsList),
        OrderFilter("Order by", getOrdersList),
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

internal open class SortFilter(name: String, vals: List<Pair<String, String>>) : SelectFilter(name, vals)
internal open class OrderFilter(name: String, vals: List<Pair<String, String>>) : SelectFilter(name, vals)

internal open class TextFilter(name: String, val type: String) : Filter.Text(name)
internal open class SelectFilter(name: String, private val vals: List<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getValue() = vals[state].second
}
private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("ID", "id"),
    Pair("Title", "title"),
    Pair("Created", "created_at"),
    Pair("Published", "published_at"),
    Pair("Pages", "pages"),
)
private val getOrdersList: List<Pair<String, String>> = listOf(
    Pair("Ascending", "asc"),
    Pair("Descending", "desc"),
)

package eu.kanade.tachiyomi.extension.all.pandachaika

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Filter.Sort.Selection
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SortFilter("Sort by", Selection(0, false), getSortsList),
        SelectFilter("Types", getTypes),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        Filter.Header("Use 'Male Tags' or 'Female Tags' for specific categories. 'Tags' searches all categories."),
        TextFilter("Tags", ""),
        TextFilter("Male Tags", "male"),
        TextFilter("Female Tags", "female"),
        TextFilter("Artists", "artist"),
        TextFilter("Parodies", "parody"),
        Filter.Separator(),
        TextFilter("Reason", "reason"),
        TextFilter("Uploader", "reason"),
        Filter.Separator(),
        Filter.Header("Filter by pages, for example: (>20)"),
        PageFilter("Pages"),
    )
}

internal open class PageFilter(name: String) : Filter.Text(name)

internal open class TextFilter(name: String, val type: String) : Filter.Text(name)

internal open class SelectFilter(name: String, val vals: List<String>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it }.toTypedArray(), state)

internal open class SortFilter(name: String, selection: Selection, private val vals: List<Pair<String, String>>) :
    Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
    fun getValue() = vals[state!!.index].second
}

private val getTypes = listOf(
    "All",
    "Doujinshi",
    "Manga",
    "Image Set",
    "Artist CG",
    "Game CG",
    "Western",
    "Non-H",
    "Misc",
)

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Public Date", "public_date"),
    Pair("Posted Date", "posted_date"),
    Pair("Title", "title"),
    Pair("Japanese Title", "title_jpn"),
    Pair("Rating", "rating"),
    Pair("Images", "images"),
    Pair("File Size", "size"),
    Pair("Category", "category"),
)

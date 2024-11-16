package eu.kanade.tachiyomi.extension.en.hentairead

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Filter.Sort.Selection
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SortFilter("Sort by", Selection(0, false), getSortsList),
        TypeFilter("Types"),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude [ Only for 'Tags' ]"),
        TextFilter("Tags", "manga_tag"),
        Filter.Separator(),
        TextFilter("Artists", "artist"),
        TextFilter("Circles", "circle"),
        TextFilter("Characters", "character"),
        TextFilter("Collections", "collection"),
        TextFilter("Scanlators", "scanlator"),
        TextFilter("Conventions", "convention"),
        Filter.Separator(),
        Filter.Header("Filter by year uploaded, for example: (>2024)"),
        UploadedFilter("Uploaded"),
        Filter.Separator(),
        Filter.Header("Filter by pages, for example: (>20)"),
        PageFilter("Pages"),
    )
}

internal open class UploadedFilter(name: String) : Filter.Text(name)

internal open class PageFilter(name: String) : Filter.Text(name)

internal open class TextFilter(name: String, val type: String) : Filter.Text(name)

internal class TypeFilter(name: String) :
    Filter.Group<CheckBoxFilter>(
        name,
        listOf(
            "Doujinshi" to "4",
            "Manga" to "52",
            "Artist CG" to "4798",
        ).map { CheckBoxFilter(it.first, it.second, true) },
    )
internal open class CheckBoxFilter(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)

internal open class SortFilter(name: String, selection: Selection, private val vals: List<Pair<String, String>>) :
    Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
    fun getValue() = vals[state!!.index].second
}

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Latest", "new"),
    Pair("A-Z", "alphabet"),
    Pair("Rating", "rating"),
    Pair("Views", "views"),
)

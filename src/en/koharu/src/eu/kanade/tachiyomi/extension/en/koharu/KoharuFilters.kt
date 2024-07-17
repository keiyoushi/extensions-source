package eu.kanade.tachiyomi.extension.en.koharu

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SortFilter("Sort by", getSortsList),
        CategoryFilter("Category"),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TextFilter("Artists", "artist"),
        TextFilter("Magazines", "magazine"),
        TextFilter("Publishers", "publisher"),
        TextFilter("Characters", "character"),
        TextFilter("Cosplayers", "cosplayer"),
        TextFilter("Parodies", "parody"),
        TextFilter("Circles", "circle"),
        TextFilter("Male Tags", "male"),
        TextFilter("Female Tags", "female"),
        TextFilter("Tags ( Universal )", "tag"),
        Filter.Header("Filter by pages, for example: (>20)"),
        TextFilter("Pages", "pages"),
    )
}

internal open class TextFilter(name: String, val type: String) : Filter.Text(name)
internal open class SortFilter(name: String, private val vals: List<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getValue() = vals[state].second
}

internal class CategoryFilter(name: String) :
    Filter.Group<CheckBoxFilter>(
        name,
        listOf(
            Pair("Manga", 2),
            Pair("Doujinshi", 4),
            Pair("Illustration", 8),
        ).map { CheckBoxFilter(it.first, it.second, true) },
    )
internal open class CheckBoxFilter(name: String, val value: Int, state: Boolean) : Filter.CheckBox(name, state)

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("ID", "1"),
    Pair("Title", "2"),
    Pair("Pages", "3"),
    Pair("Recently Posted", ""),
    Pair("Recently Updated", "5"),
    Pair("Original Posted Date", "6"),
    Pair("Most Viewed", "8"),
    Pair("Most Favorited", "9"),
)

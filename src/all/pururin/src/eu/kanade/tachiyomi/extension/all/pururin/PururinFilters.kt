package eu.kanade.tachiyomi.extension.all.pururin

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SelectFilter("Sort by", getSortsList),
        TypeFilter("Types"),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TextFilter("Tags", "[Content]"),
        TextFilter("Artists", "[Artist]"),
        TextFilter("Circles", "[Circle]"),
        TextFilter("Parodies", "[Parody]"),
        TextFilter("Languages", "[Language]"),
        TextFilter("Scanlators", "[Scanlator]"),
        TextFilter("Conventions", "[Convention]"),
        TextFilter("Collections", "[Collections]"),
        TextFilter("Categories", "[Category]"),
        TextFilter("Uploaders", "[Uploader]"),
        Filter.Separator(),
        Filter.Header("Filter by pages, for example: (>20)"),
        PageFilter("Pages"),
    )
}
internal class TypeFilter(name: String) :
    Filter.Group<CheckBoxFilter>(
        name,
        listOf(
            Pair("Artbook", "17783"),
            Pair("Artist CG", "13004"),
            Pair("Doujinshi", "13003"),
            Pair("Game CG", "13008"),
            Pair("Manga", "13004"),
            Pair("Webtoon", "27939"),
        ).map { CheckBoxFilter(it.first, it.second, true) },
    )

internal open class CheckBoxFilter(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)

internal open class PageFilter(name: String) : Filter.Text(name)

internal open class TextFilter(name: String, val type: String) : Filter.Text(name)

internal open class SelectFilter(name: String, val vals: List<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getValue() = vals[state].second
}
private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Newest", "newest"),
    Pair("Most Popular", "most-popular"),
    Pair("Highest Rated", "highest-rated"),
    Pair("Most Viewed", "most-viewed"),
    Pair("Title", "title"),
)

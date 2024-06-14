package eu.kanade.tachiyomi.extension.all.hitomi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SelectFilter("Sort by", getSortsList),
        TypeFilter("Types"),
        Filter.Separator(),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TextFilter("Groups", "group"),
        TextFilter("Artists", "artist"),
        TextFilter("Series", "series"),
        TextFilter("Characters", "character"),
        TextFilter("Male Tags", "male"),
        TextFilter("Female Tags", "female"),
        Filter.Header("Please don't put Female/Male tags here, they won't work!"),
        TextFilter("Tags", "tag"),
    )
}

internal open class TextFilter(name: String, val type: String) : Filter.Text(name)
internal open class SelectFilter(name: String, val vals: List<Triple<String, String?, String>>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getArea() = vals[state].second
    fun getValue() = vals[state].third
}
internal class TypeFilter(name: String) :
    Filter.Group<CheckBoxFilter>(
        name,
        listOf(
            Pair("Anime", "anime"),
            Pair("Artist CG", "artistcg"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Game CG", "gamecg"),
            Pair("Image Set", "imageset"),
            Pair("Manga", "manga"),
        ).map { CheckBoxFilter(it.first, it.second, true) },
    )
internal open class CheckBoxFilter(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)

private val getSortsList: List<Triple<String, String?, String>> = listOf(
    Triple("Date Added", null, "index"),
    Triple("Date Published", "date", "published"),
    Triple("Popular: Today", "popular", "today"),
    Triple("Popular: Week", "popular", "week"),
    Triple("Popular: Month", "popular", "month"),
    Triple("Popular: Year", "popular", "year"),
    Triple("Random", "popular", "year"),
)

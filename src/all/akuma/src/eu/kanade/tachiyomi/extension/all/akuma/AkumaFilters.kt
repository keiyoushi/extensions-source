package eu.kanade.tachiyomi.extension.all.akuma

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TextFilter("Female Tags", "female"),
        TextFilter("Male Tags", "male"),
        TextFilter("Other Tags", "other"),
        CategoryFilter(),
        TextFilter("Groups", "group"),
        TextFilter("Artists", "artist"),
        TextFilter("Parody", "parody"),
        TextFilter("Characters", "character"),
        Filter.Separator(),
        Filter.Header("Search in favorites, read, or commented"),
        OptionFilter(),
    )
}

internal class TextFilter(name: String, val tag: String) : Filter.Text(name)
internal class OptionFilter(val value: List<Pair<String, String>> = options) : Filter.Select<String>("Options", options.map { it.first }.toTypedArray()) {
    fun getValue() = options[state].second
}

internal open class TagTriState(name: String) : Filter.TriState(name)
internal class CategoryFilter() :
    Filter.Group<Filter.TriState>("Categories", categoryList.map { TagTriState(it) })

private val categoryList = listOf(
    "Doujinshi",
    "Manga",
    "Image Set",
    "Artist CG",
    "Game CG",
    "Western",
    "Non-H",
    "Cosplay",
    "Misc",
)
private val options = listOf(
    "None" to "",
    "Favorited only" to "favorited",
    "Read only" to "read",
    "Commented only" to "commented",
)

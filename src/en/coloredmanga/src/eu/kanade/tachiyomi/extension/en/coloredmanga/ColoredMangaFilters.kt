@file:JvmName("ColoredMangaKt")

package eu.kanade.tachiyomi.extension.en.coloredmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SortFilter("Sort by", Filter.Sort.Selection(0, false), getSortsList),
        TypeFilter("Types"),
        ColorFilter("Color"),
        StatusFilter("Status"),
        GenreFilter("Genre"),
    )
}

internal class ColorFilter(name: String) :
    Filter.Group<TriFilter>(
        name,
        listOf(
            "B/W",
            "Color",
        ).map { TriFilter(it, it.lowercase()) },
    )

internal class TypeFilter(name: String) :
    Filter.Group<TriFilter>(
        name,
        listOf(
            "Manga",
            "Manhwa",
        ).map { TriFilter(it, it.lowercase()) },
    )

internal class StatusFilter(name: String) :
    Filter.Group<TriFilter>(
        name,
        listOf(
            "Ongoing",
            "Completed",
            "Cancelled",
            "Hiatus",
        ).map { TriFilter(it, it.lowercase()) },
    )

internal class GenreFilter(name: String) : TextFilter(name)

internal open class TriFilter(name: String, val value: String) : Filter.TriState(name)

internal open class TextFilter(name: String) : Filter.Text(name)

internal open class SortFilter(name: String, selection: Selection, private val vals: List<Pair<String, String>>) :
    Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
    fun getValue() = vals[state!!.index].second
}

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Last Updated", "lat"),
    Pair("Newest", "new"),
    Pair("Popularity", "pop"),
    Pair("Title", "tit"),
)

package eu.kanade.tachiyomi.extension.all.pawchive

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
val getTypes = listOf("Patreon", "Pixiv Fanbox")

val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Popularity", "pop"),
    Pair("Date Indexed", "new"),
    Pair("Date Updated", "lat"),
    Pair("Alphabetical Order", "tit"),
    Pair("Service", "serv"),
    Pair("Date Favorited", "fav"),
)

class TypeFilter(name: String, vals: List<String>) : Filter.Group<TriFilter>(name, vals.map { TriFilter(it, it.lowercase()) })

class FavoritesFilter : Filter.Group<TriFilter>("Favorites", listOf(TriFilter("Favorites Only", "fav")))

class TriFilter(name: String, val value: String) : Filter.TriState(name)

class SortFilter(name: String, selection: Selection, private val vals: List<Pair<String, String>>) : Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
    fun getValue() = vals[state!!.index].second
}

fun getLatestUpdatesFilterList(): FilterList = FilterList(
    SortFilter("Sort by", Filter.Sort.Selection(2, false), getSortsList),
    TypeFilter("Types", getTypes),
    FavoritesFilter(),
)

fun getDefaultFilterList(): FilterList = FilterList(
    SortFilter("Sort by", Filter.Sort.Selection(0, false), getSortsList),
    TypeFilter("Types", getTypes),
    FavoritesFilter(),
)

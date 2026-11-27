package eu.kanade.tachiyomi.multisrc.kemono

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

private val sortsList: List<Pair<String, String>> = listOf(
    "Popularity" to "pop",
    "Date Indexed" to "new",
    "Date Updated" to "lat",
    "Alphabetical Order" to "tit",
    "Service" to "serv",
    "Date Favorited" to "fav",
)

private val searchModes = arrayOf("Creators", "Posts")

class SearchModeFilter : Filter.Select<String>("Search Mode", searchModes) {
    val isPostsMode: Boolean get() = state == 1
}

class TypeFilter(name: String, vals: List<String>) : Filter.Group<TriFilter>(name, vals.map { TriFilter(it, it.lowercase()) })

class FavoritesFilter : Filter.Group<TriFilter>("Favorites", listOf(TriFilter("Favorites Only", "fav")))

class TriFilter(name: String, val value: String) : Filter.TriState(name)

class SortFilter(
    name: String,
    selection: Selection,
    private val vals: List<Pair<String, String>>,
) : Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
    fun getValue() = vals[state!!.index].second
}

fun getLatestUpdatesFilterList(types: List<String>): FilterList = FilterList(
    SearchModeFilter(),
    SortFilter("Sort by", Filter.Sort.Selection(2, false), sortsList),
    TypeFilter("Types", types),
    FavoritesFilter(),
)

fun getDefaultFilterList(types: List<String>): FilterList = FilterList(
    SearchModeFilter(),
    SortFilter("Sort by", Filter.Sort.Selection(0, false), sortsList),
    TypeFilter("Types", types),
    FavoritesFilter(),
)

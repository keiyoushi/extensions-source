package eu.kanade.tachiyomi.extension.all.e621

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getE621FilterList(categoryPref: String): FilterList = FilterList(
    Filter.Header("Search by pool name"),
    DescriptionFilter(),
    OrderFilter(),
    CategoryFilter(getDefaultCategoryIndex(categoryPref)),
    ActiveOnlyFilter(),
)

private fun getDefaultCategoryIndex(categoryPref: String): Int = when (categoryPref) {
    "series" -> 1
    "collection" -> 2
    else -> 0 // "" (both) maps to "Any"
}

class OrderFilter :
    UriPartFilter(
        "Order by",
        arrayOf(
            Pair("Recently Updated", "updated_at"),
            Pair("Most Posts", "post_count"),
            Pair("Name (A-Z)", "name"),
            Pair("Newest First", "created_at"),
        ),
    )

class CategoryFilter(defaultIndex: Int = 0) :
    UriPartFilter(
        "Category",
        arrayOf(
            Pair("Any", ""),
            Pair("Series", "series"),
            Pair("Collection", "collection"),
        ),
        defaultIndex,
    )

class ActiveOnlyFilter : Filter.CheckBox("Active pools only", false)

class DescriptionFilter : Filter.Text("Description contains")

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>, defaultIndex: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultIndex) {
    fun toUriPart() = vals[state].second
}

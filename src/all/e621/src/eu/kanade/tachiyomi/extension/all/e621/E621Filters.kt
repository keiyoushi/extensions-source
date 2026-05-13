package eu.kanade.tachiyomi.extension.all.e621

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getE621FilterList(categoryPref: String): FilterList = FilterList(
    ModeFilter(),
    Filter.Separator(),
    Filter.Header("Search by pool name (Pools mode only)"),
    DescriptionFilter(),
    OrderFilter(),
    CategoryFilter(getDefaultCategoryIndex(categoryPref)),
    ActiveOnlyFilter(),
    Filter.Separator(),
    Filter.Header("Search by tags (Tags mode only)"),
    TagsFilter(),
)

fun getDefaultModeIndex(modePref: String): Int = when (modePref) {
    "pools" -> 0
    "tags" -> 1
    else -> 0
}

fun getDefaultCategoryIndex(categoryPref: String): Int = when (categoryPref) {
    "series" -> 1
    "collection" -> 2
    else -> 0 // "" (both) maps to "Any"
}

fun getDefaultOrderIndex(orderPref: String): Int = when (orderPref) {
    "updated_at" -> 0
    "post_count" -> 1
    "name" -> 2
    "created_at" -> 3
    else -> 0
}

class ModeFilter(defaultIndex: Int = 0) :
    UriPartFilter(
        "Search Mode",
        arrayOf(
            Pair("Pools", "pools.json"),
            Pair("Tags", "posts.json"),
        ),
        defaultIndex,
    )

class OrderFilter(defaultIndex: Int = 0) :
    UriPartFilter(
        "Order by",
        arrayOf(
            Pair("Recently Updated", "updated_at"),
            Pair("Most Posts", "post_count"),
            Pair("Name (A-Z)", "name"),
            Pair("Newest First", "created_at"),
        ),
        defaultIndex,
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

class TagsFilter(defaultTags: String = "") : Filter.Text("Space separated tags", defaultTags)

class ActiveOnlyFilter : Filter.CheckBox("Active pools only", false)

class DescriptionFilter : Filter.Text("Description contains")

// class TagsFilter : Filter.Text("Space separated tags")

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>, defaultIndex: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultIndex) {
    fun toUriPart() = vals[state].second
}

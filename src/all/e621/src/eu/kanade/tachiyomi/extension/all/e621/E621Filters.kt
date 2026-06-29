package eu.kanade.tachiyomi.extension.all.e621

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getE621FilterList(categoryPref: String): FilterList = FilterList(
    Filter.Header("Note: You will need to be logged into E621 via WebView to see certain posts (e.g., 'No Image')"),
    ModeFilter(),
    // Filter.Header(""),
    Filter.Separator(),
    PoolGroupFilter("Pool Search Options", categoryPref),
    // Filter.Header(""),
    Filter.Separator(),
    TagGroupFilter("Tag Search Options"),
    // Filter.Header(""),
)

class PoolGroupFilter(displayName: String, categoryPref: String) :
    Filter.Group<Any>(
        displayName,
        listOf(
            Filter.Header("(Pools Search Mode only)"),
            DescriptionFilter(),
            OrderFilter(),
            CategoryFilter(getDefaultCategoryIndex(categoryPref)),
            ActiveOnlyFilter(),
        ),
    ) {
    fun getDescription() = (state[1] as DescriptionFilter).state.trim()
    fun getOrder() = (state[2] as OrderFilter).toUriPart()
    fun getCategory() = (state[3] as CategoryFilter).toUriPart()
    fun getActiveOnly() = (state[4] as ActiveOnlyFilter).state
}

class TagGroupFilter(displayName: String) :
    Filter.Group<Any>(
        displayName,
        listOf(
            Filter.Header("(Tags Search Mode only)"),
            OrderTagFilter(),
            DateFilter(),
            TagsFilter(),
            Filter.Header("e.g.,  `anthro  -mammal  order:random  date:month  score:>100`"),
            Filter.Header("Negative tags may not filter everything"),
            // BlacklistFilter(), // Negative tags dont work well enough
            FirstPageFilter(),
            EndPageFilter(),
            Filter.Header("Warning: will filter out pools that don't use the `first_page` or `end_page` tags."),
        ),
    ) {
    fun getOrderTag() = (state[1] as OrderTagFilter).toUriPart()
    fun getDate() = (state[2] as DateFilter).toUriPart()
    fun getTags() = (state[3] as TagsFilter).state.trim()
    fun getFirstPage() = (state[6] as FirstPageFilter).state
    fun getEndPage() = (state[7] as EndPageFilter).state
}

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

fun getDefaultOrderTagsIndex(orderPref: String): Int = when (orderPref) {
    "id_desc" -> 1
    "id" -> 2
    "score" -> 3
    "hot" -> 4
    "favcount" -> 5
    "random" -> 6
    else -> 0 // "" maps to "Default"
}

fun getDefaultDateIndex(pref: String): Int = when (pref) {
    "day" -> 1
    "week" -> 2
    "month" -> 3
    "year" -> 4
    // "decade" -> 5 // Doesn't behave?
    else -> 0 // "" maps to "All Time"
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

class OrderTagFilter(defaultIndex: Int = 0) :
    UriPartFilter(
        "Order by",
        arrayOf(
            Pair("Default", ""),
            Pair("Newest", "id_desc"),
            Pair("Oldest", "id"),
            Pair("Score", "score"),
            Pair("Hot", "hot"),
            Pair("Favorites", "favcount"),
            Pair("Random", "random"),
        ),
        defaultIndex,
    )

class DateFilter(defaultIndex: Int = 0) :
    UriPartFilter(
        "Filter Date by",
        arrayOf(
            Pair("All Time", ""),
            Pair("Day", "day"),
            Pair("Week", "week"),
            Pair("Month", "month"),
            Pair("Year", "year"),
            // Pair("Decade", "decade"), // Doesn't behave?
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

class TagsFilter(defaultTags: String = "") : Filter.Text("Space Separated Tags", defaultTags)

class ActiveOnlyFilter : Filter.CheckBox("Active pools only", false)

class FirstPageFilter : Filter.CheckBox("Search tags by first pages", false)

class EndPageFilter : Filter.CheckBox("Search tags by end pages", false)

class DescriptionFilter : Filter.Text("Description contains")

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>, defaultIndex: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultIndex) {
    fun toUriPart() = vals[state].second
}

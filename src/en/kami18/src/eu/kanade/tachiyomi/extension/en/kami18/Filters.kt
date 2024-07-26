package eu.kanade.tachiyomi.extension.en.kami18

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        Filter.Header("Filter is ignored when using text search"),
        SortFilter("Sort", getSortsList),
        TimelineFilter("Timeline", getTimelinesList),
        TypeFilter("Type", getTypes),
        Filter.Separator(),
        TextFilter("Tags"),
    )
}

/** Filters **/

internal open class TextFilter(name: String) : Filter.Text(name)

internal class SortFilter(name: String, sortList: List<Pair<String, String>>, state: Int = 0) :
    SelectFilter(name, sortList, state)

internal class TypeFilter(name: String, sortList: List<Pair<String, String>>, state: Int = 0) :
    SelectFilter(name, sortList, state)

internal class TimelineFilter(name: String, sortList: List<Pair<String, String>>, state: Int = 0) :
    SelectFilter(name, sortList, state)

internal open class CheckBoxFilter(name: String, val value: String = "") : Filter.CheckBox(name)

internal open class SelectFilter(name: String, private val vals: List<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getValue() = vals[state].second
}

private val getTimelinesList: List<Pair<String, String>> = listOf(
    Pair("All Time", "a"),
    Pair("Added Today", "d"),
    Pair("Added This Week", "w"),
    Pair("Added This Month", "m"),
)

private val getTypes: List<Pair<String, String>> = listOf(
    Pair("All", ""),
    Pair("Other", "another"),
    Pair("Comic", "comic"),
    Pair("Cosplay", "cosplay"),
    Pair("Image", "image"),
    Pair("Manga", "manga"),
    Pair("Manhwa", "manhwa"),
)

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Relevant", "mm"),
    Pair("Most Recent", "mr"),
    Pair("Most Viewed", "mv"),
    Pair("Most Photos", "mp"),
    Pair("Top Rated", "tr"),
    Pair("Most Commented", "md"),
    Pair("Most Liked", "tf"),
)

package eu.kanade.tachiyomi.extension.en.manhwa18

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        Filter.Header(name = "The filter is ignored when using text search."),
        CategoryFilter("Category", Categories),
        StatusFilter("Status", Statuses),
        SortFilter("Sort", getSortsList),
        NationFilter("Nation", Nations),
        GenreFilter("Type", getTypesList),
    )
}

/** Filters **/
internal class CategoryFilter(name: String, categoryList: Map<Int, String>) :
    GroupFilter(name, categoryList.map { (value, name) -> Pair(name, value.toString()) })

internal class StatusFilter(name: String, statusList: Map<Int, String>) :
    SelectFilter(name, statusList.map { (value, name) -> Pair(name, value.toString()) })

internal class SortFilter(name: String, sortList: List<Pair<String, String>>) :
    SelectFilter(name, sortList)

internal class NationFilter(name: String, nationList: Map<Int, String>) :
    GroupFilter(name, nationList.map { (value, name) -> Pair(name, value.toString()) })

internal class GenreFilter(name: String, genreList: List<Genre>) :
    GroupFilter(name, genreList.map { Pair(it.name, it.id.toString()) })

internal open class GroupFilter(name: String, vals: List<Pair<String, String>>) :
    Filter.Group<CheckBoxFilter>(name, vals.map { CheckBoxFilter(it.first, it.second) }) {

    val checked get() = state.filter { it.state }.joinToString(",") { it.value }
}

internal open class CheckBoxFilter(name: String, val value: String = "") : Filter.CheckBox(name)

internal open class SelectFilter(name: String, private val vals: List<Pair<String, String>>) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray()) {
    fun getValue() = vals[state].second
}

internal class Genre(name: String, val id: Int) : Filter.CheckBox(name)

/** Filters Data **/
val Categories = mapOf(
    1 to "Raw",
    2 to "Sub",
)

val Nations = mapOf(
    1 to "Korea",
    2 to "Japan",
)

val Statuses = mapOf(
    0 to "In-progress",
    1 to "Completed",
)

private val getTypesList = listOf(
    Genre("Manhwa", 26),
    Genre("Action", 1),
    Genre("Adventure", 2),
    Genre("Comedy", 3),
    Genre("Drama", 4),
    Genre("Fantasy", 5),
    Genre("Horror", 6),
    Genre("Isekai", 7),
    Genre("Martial Arts", 8),
    Genre("Mystery", 9),
    Genre("Romance", 10),
    Genre("Sci-Fi", 11),
    Genre("Slice of Life", 12),
    Genre("Sports", 13),
    Genre("Supernatural", 14),
    Genre("Thriller", 15),
    Genre("Historical", 16),
    Genre("Mecha", 17),
    Genre("Psychological", 18),
    Genre("Seinen", 19),
    Genre("Shoujo", 20),
    Genre("Shounen", 21),
    Genre("Josei", 22),
    Genre("Yaoi", 23),
    Genre("Yuri", 24),
    Genre("Ecchi", 25),
)

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Most View", "most-view"),
    Pair("Most Favourite", "most-favourite"),
    Pair("A-Z", "a-z"),
    Pair("Z-A", "z-a"),
    Pair("New Updated", "new-updated"),
    Pair("Old Updated", "old-updated"),
    Pair("New Created", "new-created"),
    Pair("Old Created", "old-created"),
)

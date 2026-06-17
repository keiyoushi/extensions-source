package eu.kanade.tachiyomi.extension.en.philiascans

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Builder

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class CheckBoxVal(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(displayName: String, options: Array<Pair<String, String>>) : Filter.Group<CheckBoxVal>(displayName, options.map { CheckBoxVal(it.first, it.second) }) {
    val checked get() = state.filter { it.state }.map { it.value }
}

fun Builder.addFilter(param: String, filter: SelectFilter?) = filter?.value?.takeIf(String::isNotBlank)?.let { addQueryParameter(param, it) }

fun Builder.addFilter(param: String, filter: CheckBoxGroup?) = filter?.checked?.forEach { addQueryParameter(param, it) }

class SortFilter :
    SelectFilter(
        "Sort by",
        arrayOf(
            "Recently Updated" to "",
            "Trending" to "trending",
            "Most Viewed" to "views",
            "Highest Rating" to "rating",
            "Alphabetical" to "title",
            "New Manga" to "added",
        ),
    )

class OrderFilter :
    SelectFilter(
        "Order by",
        arrayOf(
            "Descending" to "desc",
            "Ascending" to "asc",
        ),
    )

class TypeFilter :
    CheckBoxGroup(
        "Types",
        arrayOf(
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Webtoon" to "webtoon",
            "Comic" to "comic",
        ),
    )

class StatusFilter :
    CheckBoxGroup(
        "Status",
        arrayOf(
            "On Going" to "on_going",
            "Completed" to "completed",
            "On Hold" to "on_hold",
            "Canceled" to "canceled",
        ),
    )

class GenreFilter :
    CheckBoxGroup(
        "Genre",
        arrayOf(
            "Action" to "action",
            "Adventure" to "adventure",
            "Comedy" to "comedy",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Fantasy" to "fantasy",
            "Gourmet" to "gourmet",
            "Harem" to "harem",
            "Historical" to "historical",
            "Isekai" to "isekai",
            "Josei" to "josei",
            "Magic" to "magic",
            "Martial Arts" to "martial-arts",
            "Monsters" to "monsters",
            "Music" to "music",
            "Mystery" to "mystery",
            "Psychological" to "psychological",
            "Regression" to "regression",
            "Romance" to "romance",
            "School Life" to "school-life",
            "Sci-Fi" to "sci-fi",
            "Seinen" to "seinen",
            "Shoujo" to "shoujo",
            "Shounen" to "shounen",
            "Slice of Life" to "slice-of-life",
            "Supernatural" to "supernatural",
            "Survival" to "survival",
            "Tragedy" to "tragedy",
            "Villainess" to "villainess",
            "War" to "war",
        ),
    )

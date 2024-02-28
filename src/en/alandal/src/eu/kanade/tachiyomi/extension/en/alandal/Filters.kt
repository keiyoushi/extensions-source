package eu.kanade.tachiyomi.extension.en.alandal

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter(param, vals[state].second)
    }
}

open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

open class UriMultiSelectFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Group<UriMultiSelectOption>(name, vals.map { UriMultiSelectOption(it.first, it.second) }), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val checked = state.filter { it.state }

        if (checked.isEmpty()) {
            builder.addQueryParameter(param, "-1")
        } else {
            checked.forEach {
                builder.addQueryParameter(param, it.value)
            }
        }
    }
}

class GenreFilter : UriMultiSelectFilter(
    "Genre",
    "genres",
    arrayOf(
        Pair("Action", "1"),
        Pair("Fantasy", "2"),
        Pair("Regression", "3"),
        Pair("Overpowered", "4"),
        Pair("Ascension", "5"),
        Pair("Revenge", "6"),
        Pair("Martial Arts", "7"),
        Pair("Magic", "8"),
        Pair("Necromancer", "9"),
        Pair("Adventure", "10"),
        Pair("Tower", "11"),
        Pair("Dungeons", "12"),
        Pair("Psychological", "13"),
        Pair("Isekai", "14"),
    ),
)

class SortFilter(defaultSort: String? = null) : UriPartFilter(
    "Sort By",
    "sort",
    arrayOf(
        Pair("Popularity", "popular"),
        Pair("Name", "name"),
        Pair("Chapters", "chapters"),
        Pair("Rating", "Rating"),
        Pair("New", "new"),
    ),
    defaultSort,
)

class StatusFilter : UriPartFilter(
    "Status",
    "status",
    arrayOf(
        Pair("Any", "-1"),
        Pair("Ongoing", "1"),
        Pair("Coming Soon", "5"),
        Pair("Completed", "6"),
    ),
)

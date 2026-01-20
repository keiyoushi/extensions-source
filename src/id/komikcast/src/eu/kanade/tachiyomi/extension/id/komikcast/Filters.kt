package eu.kanade.tachiyomi.extension.id.komikcast

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
    private val default: String = "",
) : UriFilter, Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second == default }.takeIf { it != -1 } ?: 0,
) {
    override fun addToUri(builder: HttpUrl.Builder) {
        val selected = vals[state].second
        if (selected.isNotEmpty()) {
            builder.addQueryParameter(param, selected)
        }
    }
}

open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

open class UriMultiSelectFilter(
    name: String,
    private val param: String,
    private val options: Array<Pair<String, String>>,
) : UriFilter, Filter.Group<UriMultiSelectOption>(
    name,
    options.map { UriMultiSelectOption(it.first, it.second) },
) {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            builder.addQueryParameter(param, it.value)
        }
    }
}

open class UriMultiTriSelectOption(name: String, val value: String) : Filter.TriState(name)

open class UriMultiTriSelectFilter(
    name: String,
    private val includeParam: String,
    private val excludeParam: String,
    private val options: Array<Pair<String, String>>,
) : UriFilter, Filter.Group<UriMultiTriSelectOption>(
    name,
    options.map { UriMultiTriSelectOption(it.first, it.second) },
) {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.forEach {
            if (it.isIncluded()) {
                builder.addQueryParameter(includeParam, it.value)
            }
            if (it.isExcluded()) {
                builder.addQueryParameter(excludeParam, "-${it.value}")
            }
        }
    }
}

class SortFilter(default: String = "") : UriPartFilter(
    "Sort",
    "sort",
    arrayOf(
        Pair("Popularitas", "popular"),
        Pair("Terbaru", "latest"),
        Pair("Rating", "rating"),
    ),
    default,
)

class SortOrderFilter : UriPartFilter(
    "Sort Order",
    "sortOrder",
    arrayOf(
        Pair("Desc", "desc"),
        Pair("Asc", "asc"),
    ),
)

class StatusFilter : UriMultiSelectFilter(
    "Status",
    "status",
    arrayOf(
        Pair("On Going", "ongoing"),
        Pair("Completed", "completed"),
        Pair("Hiatus", "hiatus"),
        Pair("Cancelled", "cancelled"),
    ),
)

class FormatFilter : UriMultiSelectFilter(
    "Format",
    "format",
    arrayOf(
        Pair("Manga", "manga"),
        Pair("Manhwa", "manhwa"),
        Pair("Manhua", "manhua"),
        Pair("Webtoon", "webtoon"),
    ),
)

class TypeFilter : UriMultiSelectFilter(
    "Type",
    "type",
    arrayOf(
        Pair("Project", "project"),
        Pair("Mirror", "mirror"),
    ),
)

class GenreFilter(genres: Array<Pair<String, String>>) : UriMultiTriSelectFilter(
    "Genre",
    "genreIds",
    "genreIds",
    genres,
)

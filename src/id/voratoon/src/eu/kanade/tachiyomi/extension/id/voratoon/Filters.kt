package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToFilter(builder: StringBuilder)
}

interface UriQueryFilter {
    fun addToQuery(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val field: String,
    private val vals: Array<Pair<String, String>>,
    private val default: String = "",
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second == default }.takeIf { it != -1 } ?: 0,
),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        val selected = vals[state].second
        if (selected.isNotEmpty()) {
            builder.addQueryParameter(field, selected)
        }
    }
}

open class UriMultiSelectOption(name: String, val value: String) : Filter.CheckBox(name)

open class UriMultiSelectFilter(
    name: String,
    private val field: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Group<UriMultiSelectOption>(
    name,
    options.map { UriMultiSelectOption(it.first, it.second) },
),
    UriFilter {
    override fun addToFilter(builder: StringBuilder) {
        state.filter { it.state }.forEach {
            appendFilter(builder, "$field==${it.value}")
        }
    }
}

private fun appendFilter(builder: StringBuilder, filter: String) {
    if (builder.isNotEmpty()) {
        builder.append(';')
    }
    builder.append(filter)
}

class SortFilter :
    UriPartFilter(
        "Sort",
        "sort",
        arrayOf(
            Pair("Popular", "totalViews"),
            Pair("Terbaru", "latest"),
            Pair("Rating", "rating"),
            Pair("A-Z", "title"),
        ),
    )

class SortOrderFilter :
    UriPartFilter(
        "Sort Order",
        "sortOrder",
        arrayOf(
            Pair("Desc", "desc"),
            Pair("Asc", "asc"),
        ),
        default = "desc",
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        arrayOf(
            Pair("Any", ""),
            Pair("On Going", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
            Pair("Cancelled", "cancelled"),
        ),
    )

class FormatFilter :
    UriPartFilter(
        "Format",
        "format",
        arrayOf(
            Pair("Any", ""),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Webtoon", "webtoon"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Type",
        "type",
        arrayOf(
            Pair("Any", ""),
            Pair("Project", "project"),
            Pair("Mirror", "mirror"),
        ),
    )

class GenreFilter(genres: Array<Pair<String, String>>) :
    UriMultiSelectFilter(
        "Genre",
        "genreIds",
        genres,
    )

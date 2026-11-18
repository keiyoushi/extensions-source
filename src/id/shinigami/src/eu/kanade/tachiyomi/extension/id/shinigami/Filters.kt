package eu.kanade.tachiyomi.extension.id.shinigami

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Filter.Sort.Selection
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
    private val default: String = "",
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second == default }.takeIf { it != -1 } ?: 0,
),
    UriFilter {
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
) : Filter.Group<UriMultiSelectOption>(
    name,
    options.map { UriMultiSelectOption(it.first, it.second) },
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val selected = state.filter { it.state }.map { it.value }
        if (selected.isNotEmpty()) {
            builder.addQueryParameter(param, selected.joinToString(","))
        }
    }
}

class SortFilter :
    Filter.Sort(
        "Sort",
        arrayOf("Latest Updates", "New Series", "Popularity", "Rating", "A-Z"),
        Selection(0, false),
    ) {
    val sort get() = when (state?.index) {
        1 -> "new_manga"
        2 -> "popularity"
        3 -> "rating"
        4 -> "title"
        else -> "latest"
    }
    val order get() = if (state?.index == 4 || state?.ascending == true) "asc" else "desc"
}

class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
        ),
    )

class FormatFilter :
    UriMultiSelectFilter(
        "Format",
        "format",
        arrayOf(
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
        ),
    )

class TypeFilter :
    UriMultiSelectFilter(
        "Type",
        "type",
        arrayOf(
            Pair("Project", "project"),
            Pair("Mirror", "mirror"),
        ),
    )

class GenreFilter(options: Array<Pair<String, String>>) : UriMultiSelectFilter("Genre", "genre", options)

class ThemeFilter(options: Array<Pair<String, String>>) : UriMultiSelectFilter("Theme", "theme", options)

class SubFormatFilter :
    UriMultiSelectFilter(
        "Sub-format",
        "subformat",
        arrayOf(
            Pair("Long Strip", "long-strip"),
            Pair("Web Comic", "web-comic"),
            Pair("Adaptation", "adaptation-2"),
            Pair("Full Color", "full-color"),
        ),
    )

class ChapterRangeFilter :
    UriPartFilter(
        "Chapters",
        "min_chapters",
        arrayOf(
            Pair("All", ""),
            Pair("10+", "10"),
            Pair("50+", "50"),
            Pair("100+", "100"),
            Pair("200+", "200"),
        ),
    )

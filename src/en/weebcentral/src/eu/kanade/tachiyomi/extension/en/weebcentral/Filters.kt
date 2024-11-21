package eu.kanade.tachiyomi.extension.en.weebcentral

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
        builder.addQueryParameter(param, vals[state].second)
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
    private val includeUrlParameter: String,
    private val excludeUrlParameter: String,
    private val options: Array<Pair<String, String>>,
) : UriFilter, Filter.Group<UriMultiTriSelectOption>(
    name,
    options.map { UriMultiTriSelectOption(it.first, it.second) },
) {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.forEach {
            if (it.isIncluded()) {
                builder.addQueryParameter(includeUrlParameter, it.value)
            }
            if (it.isExcluded()) {
                builder.addQueryParameter(excludeUrlParameter, it.value)
            }
        }
    }
}

class SortFilter(default: String = "") : UriPartFilter(
    "Sort",
    "sort",
    arrayOf(
        Pair("Best Match", "Best Match"),
        Pair("Alphabet", "Alphabet"),
        Pair("Popularity", "Popularity"),
        Pair("Subscribers", "Subscribers"),
        Pair("Recently Added", "Recently Added"),
        Pair("Latest Updates", "Latest Updates"),
    ),
    default,
)

class SortOrderFilter : UriPartFilter(
    "Sort Order",
    "order",
    arrayOf(
        Pair("Descending", "Descending"),
        Pair("Ascending", "Ascending"),
    ),
)

class OfficialTranslationFilter : UriPartFilter(
    "Official Translation",
    "official",
    arrayOf(
        Pair("Any", "Any"),
        Pair("True", "True"),
        Pair("False", "False"),
    ),
)

class StatusFilter : UriMultiSelectFilter(
    "Series Status",
    "included_status",
    arrayOf(
        Pair("Ongoing", "Ongoing"),
        Pair("Complete", "Complete"),
        Pair("Hiatus", "Hiatus"),
        Pair("Canceled", "Canceled"),
    ),
)

class TypeFilter : UriMultiSelectFilter(
    "Series Type",
    "included_type",
    arrayOf(
        Pair("Manga", "Manga"),
        Pair("Manhwa", "Manhwa"),
        Pair("Manhua", "Manhua"),
        Pair("OEL", "OEL"),
    ),
)

class TagFilter : UriMultiTriSelectFilter(
    "Tags",
    "included_tag",
    "excluded_tag",
    arrayOf(
        Pair("Action", "Action"),
        Pair("Adult", "Adult"),
        Pair("Adventure", "Adventure"),
        Pair("Comedy", "Comedy"),
        Pair("Doujinshi", "Doujinshi"),
        Pair("Drama", "Drama"),
        Pair("Ecchi", "Ecchi"),
        Pair("Fantasy", "Fantasy"),
        Pair("Gender Bender", "Gender Bender"),
        Pair("Harem", "Harem"),
        Pair("Hentai", "Hentai"),
        Pair("Historical", "Historical"),
        Pair("Horror", "Horror"),
        Pair("Isekai", "Isekai"),
        Pair("Josei", "Josei"),
        Pair("Lolicon", "Lolicon"),
        Pair("Martial Arts", "Martial Arts"),
        Pair("Mature", "Mature"),
        Pair("Mecha", "Mecha"),
        Pair("Mystery", "Mystery"),
        Pair("Psychological", "Psychological"),
        Pair("Romance", "Romance"),
        Pair("School Life", "School Life"),
        Pair("Sci-fi", "Sci-fi"),
        Pair("Seinen", "Seinen"),
        Pair("Shotacon", "Shotacon"),
        Pair("Shoujo", "Shoujo"),
        Pair("Shoujo Ai", "Shoujo Ai"),
        Pair("Shounen", "Shounen"),
        Pair("Shounen Ai", "Shounen Ai"),
        Pair("Slice of Life", "Slice of Life"),
        Pair("Smut", "Smut"),
        Pair("Sports", "Sports"),
        Pair("Supernatural", "Supernatural"),
        Pair("Tragedy", "Tragedy"),
        Pair("Yaoi", "Yaoi"),
        Pair("Yuri", "Yuri"),
        Pair("Other", "Other"),
    ),
)

package eu.kanade.tachiyomi.extension.id.shinigami

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

open class UriMultiTriSelectOption(name: String, val value: String) : Filter.TriState(name)

open class UriMultiTriSelectFilter(
    name: String,
    private val includeParam: String,
    private val excludeParam: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Group<UriMultiTriSelectOption>(
    name,
    options.map { UriMultiTriSelectOption(it.first, it.second) },
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val included = state.filter { it.isIncluded() }.map { it.value }
        val excluded = state.filter { it.isExcluded() }.map { it.value }

        if (included.isNotEmpty()) {
            builder.addQueryParameter(includeParam, included.joinToString(","))
            builder.addQueryParameter("genre_include_mode", "and")
        }
        if (excluded.isNotEmpty()) {
            builder.addQueryParameter(excludeParam, excluded.joinToString(","))
            builder.addQueryParameter("genre_exclude_mode", "and")
        }
    }
}

class SortFilter :
    UriPartFilter(
        "Sort",
        "sort",
        arrayOf(
            Pair("Default", ""),
            Pair("Latest", "latest"),
            Pair("Popularity", "popularity"),
            Pair("Rating", "rating"),
        ),
    )

class SortOrderFilter :
    UriPartFilter(
        "Sort Order",
        "sort_order",
        arrayOf(
            Pair("Descending", "desc"),
            Pair("Ascending", "asc"),
        ),
    )

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

class GenreFilter :
    UriMultiTriSelectFilter(
        "Genre",
        "genre_include",
        "genre_exclude",
        GENRES,
    )

private val GENRES = arrayOf(
    Pair("Action", "action"),
    Pair("Adaptation", "adaptation"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("Comedy", "comedy"),
    Pair("Cooking", "cooking"),
    Pair("Crime", "crime"),
    Pair("Demon", "demon"),
    Pair("Demons", "demons"),
    Pair("Dra", "dra-genre"),
    Pair("Drama", "drama"),
    Pair("Ecchi", "ecchi"),
    Pair("Fantasy", "fantasy"),
    Pair("Fight", "fight"),
    Pair("Game", "game"),
    Pair("Gender Bender", "gender-bender"),
    Pair("Harem", "harem"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Isekai", "isekai"),
    Pair("Josei", "josei-genre"),
    Pair("Latest", "latest"),
    Pair("Love", "love"),
    Pair("Magic", "magic"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Mature", "mature"),
    Pair("Mecha", "mecha"),
    Pair("Medical", "medical"),
    Pair("Murim", "murim"),
    Pair("Mystery", "mystery"),
    Pair("Philosophical", "philosophical"),
    Pair("Psychological", "psychological"),
    Pair("Regression", "regression"),
    Pair("Revenge", "revenge"),
    Pair("Romance", "romance"),
    Pair("School Life", "school-life"),
    Pair("Sci-fi", "sci-fi"),
    Pair("Seinen", "seinen"),
    Pair("Shoujo", "shoujo"),
    Pair("Shounen", "shounen"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Smut", "smut"),
    Pair("Sports", "sports"),
    Pair("Supernatural", "supernatural"),
    Pair("Supranatural", "supranatural"),
    Pair("Thriller", "thriller"),
    Pair("Tragedy", "tragedy"),
    Pair("Violence", "violence"),
    Pair("Wuxia", "wuxia"),
)

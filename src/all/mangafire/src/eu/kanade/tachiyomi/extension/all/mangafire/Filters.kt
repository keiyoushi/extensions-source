package eu.kanade.tachiyomi.extension.all.mangafire

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl
import java.util.Calendar

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

        checked.forEach {
            builder.addQueryParameter(param, it.value)
        }
    }
}

open class UriTriSelectOption(name: String, val value: String) : Filter.TriState(name)

open class UriTriSelectFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Group<UriTriSelectOption>(name, vals.map { UriTriSelectOption(it.first, it.second) }), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.forEach { s ->
            when (s.state) {
                TriState.STATE_INCLUDE -> builder.addQueryParameter(param, s.value)
                TriState.STATE_EXCLUDE -> builder.addQueryParameter(param, "-${s.value}")
            }
        }
    }
}

class TypeFilter : UriMultiSelectFilter(
    "Type",
    "type",
    arrayOf(
        Pair("Manga", "manga"),
        Pair("One-Shot", "one_shot"),
        Pair("Doujinshi", "doujinshi"),
        Pair("Novel", "novel"),
        Pair("Manhwa", "manhwa"),
        Pair("Manhua", "manhua"),
    ),
)

class GenreFilter : UriTriSelectFilter(
    "Genres",
    "genre[]",
    arrayOf(
        Pair("Action", "1"),
        Pair("Adventure", "78"),
        Pair("Avant Garde", "3"),
        Pair("Boys Love", "4"),
        Pair("Comedy", "5"),
        Pair("Demons", "77"),
        Pair("Drama", "6"),
        Pair("Ecchi", "7"),
        Pair("Fantasy", "79"),
        Pair("Girls Love", "9"),
        Pair("Gourmet", "10"),
        Pair("Harem", "11"),
        Pair("Horror", "530"),
        Pair("Isekai", "13"),
        Pair("Iyashikei", "531"),
        Pair("Josei", "15"),
        Pair("Kids", "532"),
        Pair("Magic", "539"),
        Pair("Mahou Shoujo", "533"),
        Pair("Martial Arts", "534"),
        Pair("Mecha", "19"),
        Pair("Military", "535"),
        Pair("Music", "21"),
        Pair("Mystery", "22"),
        Pair("Parody", "23"),
        Pair("Psychological", "536"),
        Pair("Reverse Harem", "25"),
        Pair("Romance", "26"),
        Pair("School", "73"),
        Pair("Sci-Fi", "28"),
        Pair("Seinen", "537"),
        Pair("Shoujo", "30"),
        Pair("Shounen", "31"),
        Pair("Slice of Life", "538"),
        Pair("Space", "33"),
        Pair("Sports", "34"),
        Pair("Super Power", "75"),
        Pair("Supernatural", "76"),
        Pair("Suspense", "37"),
        Pair("Thriller", "38"),
        Pair("Vampire", "39"),
    ),
)

class GenreModeFilter : Filter.CheckBox("Must have all the selected genres"), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (state) {
            builder.addQueryParameter("genre_mode", "and")
        }
    }
}

class StatusFilter : UriMultiSelectFilter(
    "Status",
    "status[]",
    arrayOf(
        Pair("Completed", "completed"),
        Pair("Releasing", "releasing"),
        Pair("On Hiatus", "on_hiatus"),
        Pair("Discontinued", "discontinued"),
        Pair("Not Yet Published", "info"),
    ),
)

class YearFilter : UriMultiSelectFilter(
    "Year",
    "year[]",
    years,
) {
    companion object {
        private val currentYear by lazy {
            Calendar.getInstance()[Calendar.YEAR]
        }

        private val years: Array<Pair<String, String>> = buildList(29) {
            addAll(
                (currentYear downTo (currentYear - 20)).map(Int::toString),
            )

            addAll(
                (2000 downTo 1930 step 10).map { "${it}s" },
            )
        }.map { Pair(it, it) }.toTypedArray()
    }
}

class MinChapterFilter : Filter.Text("Minimum chapter length"), UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        if (state.isNotEmpty()) {
            val value = state.toIntOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Minimum chapter length must be a positive integer greater than 0")

            builder.addQueryParameter("minchap", value.toString())
        }
    }
}

class SortFilter(defaultValue: String? = null) : UriPartFilter(
    "Sort",
    "sort",
    arrayOf(
        Pair("Most relevance", "most_relevance"),
        Pair("Recently updated", "recently_updated"),
        Pair("Recently added", "recently_added"),
        Pair("Release date", "release_date"),
        Pair("Trending", "trending"),
        Pair("Name A-Z", "title_az"),
        Pair("Scores", "scores"),
        Pair("MAL scores", "mal_scores"),
        Pair("Most viewed", "most_viewed"),
        Pair("Most favourited", "most_favourited"),
    ),
    defaultValue,
)

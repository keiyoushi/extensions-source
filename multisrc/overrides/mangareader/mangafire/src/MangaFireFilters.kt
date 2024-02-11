package eu.kanade.tachiyomi.extension.all.mangafire

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriFilter
import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader.UriMultiSelectFilter
import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl
import java.util.Calendar

open class MFUriPartFilter(
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
        val value = vals[state].second
        if (value.isNotBlank()) {
            builder.addQueryParameter(param, value)
        }
    }
}

class UriTriMultiSelectOption(name: String, val value: String) : Filter.TriState(name)

open class UriTriMultiSelectFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Group<UriTriMultiSelectOption>(
    name,
    vals.map { UriTriMultiSelectOption(it.first, it.second) },
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state
            .filter { it.state != TriState.STATE_IGNORE }
            .forEach { s ->
                if (s.state == TriState.STATE_INCLUDE) {
                    builder.addQueryParameter(param, s.value)
                } else if (s.state == TriState.STATE_EXCLUDE) {
                    builder.addQueryParameter(param, "-${s.value}")
                }
            }
    }
}

class TypeFilter : UriMultiSelectFilter(
    "Type",
    "type[]",
    arrayOf(
        Pair("Manga", "manga"),
        Pair("One-Shot", "one_shot"),
        Pair("Doujinshi", "doujinshi"),
        Pair("Light-Novel", "light_novel"),
        Pair("Novel", "novel"),
        Pair("Manhwa", "manhwa"),
        Pair("Manhua", "manhua"),
    ),
)

class GenreFilter : UriTriMultiSelectFilter(
    "Genre",
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

class GenreModeFilter : MFUriPartFilter(
    "Must include all genres",
    "genre_mode",
    arrayOf(
        Pair("No", ""),
        Pair("Yes", "and"),
    ),
)

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

        private val years = (currentYear downTo 2004).map { year ->
            Pair(year.toString(), year.toString())
        }.toTypedArray() + arrayOf(
            Pair("2000s", "2000s"),
            Pair("1990s", "1990s"),
            Pair("1980s", "1980s"),
            Pair("1970s", "1970s"),
            Pair("1960s", "1960s"),
            Pair("1950s", "1950s"),
            Pair("1940s", "1940s"),
            Pair("1930s", "1930s"),
        )
    }
}

class ChapterCountFilter : MFUriPartFilter(
    "Chapter Count",
    "minchap",
    arrayOf(
        Pair("Any", ""),
        Pair("At least 1 chapter", "1"),
        Pair("At least 3 chapters", "3"),
        Pair("At least 5 chapters", "5"),
        Pair("At least 10 chapters", "10"),
        Pair("At least 20 chapters", "20"),
        Pair("At least 30 chapters", "30"),
        Pair("At least 50 chapters", "50"),
    ),
)

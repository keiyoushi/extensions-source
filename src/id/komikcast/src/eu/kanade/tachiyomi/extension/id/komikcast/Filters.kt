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
) : Filter.Group<UriMultiTriSelectOption>(
    name,
    options.map { UriMultiTriSelectOption(it.first, it.second) },
),
    UriFilter {
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

class SortFilter(default: String = "") :
    UriPartFilter(
        "Sort",
        "sort",
        arrayOf(
            Pair("Popularitas", "popular"),
            Pair("Terbaru", "latest"),
            Pair("Rating", "rating"),
        ),
        default,
    )

class SortOrderFilter :
    UriPartFilter(
        "Sort Order",
        "sortOrder",
        arrayOf(
            Pair("Desc", "desc"),
            Pair("Asc", "asc"),
        ),
    )

class StatusFilter :
    UriMultiSelectFilter(
        "Status",
        "status",
        arrayOf(
            Pair("On Going", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
            Pair("Cancelled", "cancelled"),
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
            Pair("Webtoon", "webtoon"),
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

fun getGenres(): Array<Pair<String, String>> = arrayOf(
    Pair("4-Koma", "4-Koma"),
    Pair("Adventure", "Adventure"),
    Pair("Cooking", "Cooking"),
    Pair("Game", "Game"),
    Pair("Gore", "Gore"),
    Pair("Harem", "Harem"),
    Pair("Historical", "Historical"),
    Pair("Horror", "Horror"),
    Pair("Isekai", "Isekai"),
    Pair("Josei", "Josei"),
    Pair("Magic", "Magic"),
    Pair("Martial Arts", "Martial Arts"),
    Pair("Mature", "Mature"),
    Pair("Mecha", "Mecha"),
    Pair("Medical", "Medical"),
    Pair("Military", "Military"),
    Pair("Music", "Music"),
    Pair("Mystery", "Mystery"),
    Pair("One-Shot", "One-Shot"),
    Pair("Police", "Police"),
    Pair("Psychological", "Psychological"),
    Pair("Reincarnation", "Reincarnation"),
    Pair("Romance", "Romance"),
    Pair("School", "School"),
    Pair("School Life", "School Life"),
    Pair("Sci-Fi", "Sci-Fi"),
    Pair("Seinen", "Seinen"),
    Pair("Shoujo", "Shoujo"),
    Pair("Shoujo Ai", "Shoujo Ai"),
    Pair("Action", "Action"),
    Pair("Comedy", "Comedy"),
    Pair("Demons", "Demons"),
    Pair("Drama", "Drama"),
    Pair("Ecchi", "Ecchi"),
    Pair("Fantasy", "Fantasy"),
    Pair("Gender Bender", "Gender Bender"),
    Pair("Shounen", "Shounen"),
    Pair("Shounen Ai", "Shounen Ai"),
    Pair("Slice of Life", "Slice of Life"),
    Pair("Sports", "Sports"),
    Pair("Super Power", "Super Power"),
    Pair("Supernatural", "Supernatural"),
    Pair("Thriller", "Thriller"),
    Pair("Tragedy", "Tragedy"),
    Pair("Vampire", "Vampire"),
    Pair("Webtoons", "Webtoons"),
    Pair("Yuri", "Yuri"),
)

class GenreFilter(genres: Array<Pair<String, String>>) :
    UriMultiTriSelectFilter(
        "Genre",
        "genreIds",
        "genreIds",
        genres,
    )

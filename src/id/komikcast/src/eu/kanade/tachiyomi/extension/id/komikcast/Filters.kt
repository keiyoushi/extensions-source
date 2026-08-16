package eu.kanade.tachiyomi.extension.id.komikcast

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

class SortFilter(default: String = "") :
    UriPartFilter(
        "Sort",
        "sort",
        arrayOf(
            Pair("Popular", "totalViews"),
            Pair("Terbaru", "latest"),
            Pair("Rating", "rating"),
            Pair("A-Z", "title"),
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
        default = "desc",
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
    Pair("4-Koma", "11"),
    Pair("Action", "19"),
    Pair("Adult", "49"),
    Pair("Adventure", "16"),
    Pair("Comedy", "22"),
    Pair("Cooking", "7"),
    Pair("Demons", "40"),
    Pair("Drama", "29"),
    Pair("Ecchi", "47"),
    Pair("Fantasy", "28"),
    Pair("Game", "17"),
    Pair("Gender Bender", "41"),
    Pair("Gore", "15"),
    Pair("Harem", "34"),
    Pair("Historical", "10"),
    Pair("Horror", "18"),
    Pair("Isekai", "6"),
    Pair("Josei", "23"),
    Pair("Magic", "35"),
    Pair("Martial Arts", "46"),
    Pair("Mature", "37"),
    Pair("Mecha", "20"),
    Pair("Medical", "9"),
    Pair("Military", "36"),
    Pair("Music", "44"),
    Pair("Mystery", "39"),
    Pair("One-Shot", "33"),
    Pair("Police", "43"),
    Pair("Psychological", "4"),
    Pair("Reincarnation", "14"),
    Pair("Romance", "26"),
    Pair("School", "42"),
    Pair("School Life", "2"),
    Pair("Sci-Fi", "32"),
    Pair("Seinen", "1"),
    Pair("Shoujo", "31"),
    Pair("Shoujo Ai", "27"),
    Pair("Shounen", "30"),
    Pair("Shounen Ai", "25"),
    Pair("Slice of Life", "13"),
    Pair("Sports", "12"),
    Pair("Super Power", "45"),
    Pair("Supernatural", "24"),
    Pair("Thriller", "5"),
    Pair("Tragedy", "38"),
    Pair("Vampire", "21"),
    Pair("Webtoons", "8"),
    Pair("Yuri", "3"),
)

class GenreFilter(genres: Array<Pair<String, String>>) :
    UriMultiSelectFilter(
        "Genre",
        "genreIds",
        genres,
    )

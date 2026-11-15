package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriQueryFilter {
    fun addToQuery(builder: HttpUrl.Builder)
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
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        val selected = vals[state].second
        if (selected.isNotEmpty()) {
            builder.addQueryParameter(param, selected)
        }
    }
}

class SortFilter :
    UriPartFilter(
        "Urutkan",
        "sort",
        arrayOf(
            Pair("Terbaru", "new"),
            Pair("Populer", "popular"),
            Pair("Rating", "rating"),
            Pair("A - Z", "az"),
            Pair("Seri Baru", "newseries"),
            Pair("Favorit", "bookmark"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "ongoing"),
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
            Pair("Semua", ""),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
            Pair("Webtoon", "webtoon"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipe",
        "type",
        arrayOf(
            Pair("Semua", ""),
            Pair("Project", "project"),
            Pair("Mirror", "mirror"),
        ),
    )

class AdaptationFilter :
    UriPartFilter(
        "Adaptasi",
        "anime",
        arrayOf(
            Pair("Semua", ""),
            Pair("Animated", "yes"),
            Pair("Yet Animated", "no"),
        ),
    )

class MinChapterFilter :
    Filter.Text("Minimum Chapter"),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        val value = state.trim().toIntOrNull()?.takeIf { it > 0 }
        if (value != null) {
            builder.addQueryParameter("min", value.toString())
        }
    }
}

class GenreOption(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(genres: Array<Pair<String, String>>) :
    Filter.Group<GenreOption>(
        "Genre",
        genres.map { GenreOption(it.first, it.second) },
    ),
    UriQueryFilter {
    override fun addToQuery(builder: HttpUrl.Builder) {
        val selected = state.filter { it.state }.map { it.value }
        if (selected.isNotEmpty()) {
            builder.addQueryParameter("genre", selected.joinToString(","))
        }
    }
}

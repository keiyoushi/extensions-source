package eu.kanade.tachiyomi.extension.id.ikiru

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = options[state].second
        if (value.isNotEmpty()) {
            builder.addQueryParameter(param, value)
        }
    }
}

class SortFilter :
    UriPartFilter(
        "Urutkan",
        "sort",
        arrayOf(
            Pair("Populer", "popular"),
            Pair("Baru ditambahkan", "added"),
            Pair("Update terbaru", "update"),
            Pair("Rating", "rating"),
            Pair("Judul A-Z", "title"),
        ),
    )

class TypeFilter :
    UriPartFilter(
        "Tipe",
        "type",
        arrayOf(
            Pair("Semua", ""),
            Pair("Manhwa", "Manhwa"),
            Pair("Manga", "Manga"),
            Pair("Manhua", "Manhua"),
            Pair("Novel", "Novel"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        "status",
        arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "Ongoing"),
            Pair("Completed", "Completed"),
        ),
    )

class SourceFilter :
    UriPartFilter(
        "Sumber",
        "source",
        arrayOf(
            Pair("Semua", ""),
            Pair("Project", "project"),
            Pair("Mirror", "mirror"),
        ),
    )

class Genre(name: String, val slug: String) : Filter.TriState(name)

class GenreFilter(genres: List<Genre>) :
    Filter.Group<Genre>("Genre", genres),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val included = state.filter { it.isIncluded() }.map { it.slug }
        val excluded = state.filter { it.isExcluded() }.map { it.slug }
        if (included.isNotEmpty()) {
            builder.addQueryParameter("genre", included.joinToString(","))
        }
        if (excluded.isNotEmpty()) {
            builder.addQueryParameter("exclude", excluded.joinToString(","))
        }
    }
}

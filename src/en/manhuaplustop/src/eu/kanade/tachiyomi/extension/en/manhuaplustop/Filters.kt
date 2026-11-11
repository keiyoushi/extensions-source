package eu.kanade.tachiyomi.extension.en.manhuaplustop

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

class Genre(name: String, val id: String) : Filter.TriState(name)

class GenresFilter(genres: List<Pair<String, String>>) :
    Filter.Group<Genre>("Genres", genres.map { Genre(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        url.addQueryParameter("genres", state.filter { it.isIncluded() }.joinToString(",") { it.id })
        url.addQueryParameter("notGenres", state.filter { it.isExcluded() }.joinToString(",") { it.id })
    }
}

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    private val parameter: String,
    defaultIndex: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), defaultIndex),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        url.addQueryParameter(parameter, options[state].second)
    }
}

class ChapterCountFilter(options: List<Pair<String, String>>) : SelectFilter("Chapter count", options, "chapter_count")

class SeriesStatusFilter(options: List<Pair<String, String>>) : SelectFilter("Status", options, "status")

class SexFilter(options: List<Pair<String, String>>) : SelectFilter("Sex", options, "sex")

class SortFilter(options: List<Pair<String, String>>) : SelectFilter("Order by", options, "sort", options.indexOfFirst { it.second == "views" }.coerceAtLeast(0))

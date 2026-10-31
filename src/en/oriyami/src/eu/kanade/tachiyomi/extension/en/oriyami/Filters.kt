package eu.kanade.tachiyomi.extension.en.oriyami

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addTo(url: HttpUrl.Builder)
}

class SortFilter :
    Filter.Select<String>("Sort", SORTS.map { it.first }.toTypedArray(), 2),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        SORTS[state].second?.let { url.addQueryParameter("sort", it) }
    }

    companion object {
        private val SORTS = listOf(
            "Alphabetical" to null,
            "Trending" to "trending",
            "Popular" to "popularity",
            "Latest" to "latest_updates",
            "Top Rated" to "rating",
            "Most Followed" to "followers",
            "Most Chapters" to "chapters",
            "Release Year" to "release_date",
        )
    }
}

class TriStateOption(name: String) : Filter.TriState(name)

class TriStateGroup(name: String, private val param: String, values: List<String>) :
    Filter.Group<TriStateOption>(name, values.map(::TriStateOption)),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        val included = state.filter { it.isIncluded() }.map { it.name }
        val excluded = state.filter { it.isExcluded() }.map { it.name }
        if (included.isNotEmpty()) url.addQueryParameter(param, included.joinToString(","))
        if (excluded.isNotEmpty()) url.addQueryParameter("exclude_$param", excluded.joinToString(","))
    }
}

class AnimeFilter :
    Filter.Select<String>("Anime adaptation", arrayOf("All", "Has anime", "No anime")),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        when (state) {
            1 -> url.addQueryParameter("has_anime_adaptation", "1")
            2 -> url.addQueryParameter("has_anime_adaptation", "0")
        }
    }
}

package eu.kanade.tachiyomi.extension.en.desirescans

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

fun getFilters() = FilterList(
    SortFilter(),
    GenreFilter(),
    CheckBoxGroup("Type", "type", TYPES),
    CheckBoxGroup("Status", "status", STATUSES),
)

interface UrlFilter {
    fun addTo(url: HttpUrl.Builder)
}

class SortFilter :
    Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        url.addQueryParameter("sort", SORTS[state].second)
    }
}

class Genre(name: String, val slug: String) : Filter.TriState(name)

class GenreFilter :
    Filter.Group<Genre>("Genres", GENRES.map { Genre(it.first, it.second) }),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        state.filter { it.isIncluded() }.ifEmpty { null }?.let { url.addQueryParameter("genre", it.joinToString(",") { genre -> genre.slug }) }
        state.filter { it.isExcluded() }.ifEmpty { null }?.let { url.addQueryParameter("exgenre", it.joinToString(",") { genre -> genre.slug }) }
    }
}

class CheckBox(name: String, val value: String) : Filter.CheckBox(name)

class CheckBoxGroup(name: String, private val param: String, options: List<Pair<String, String>>) :
    Filter.Group<CheckBox>(name, options.map { CheckBox(it.first, it.second) }),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        state.filter { it.state }.ifEmpty { null }?.let { url.addQueryParameter(param, it.joinToString(",") { box -> box.value }) }
    }
}

private val SORTS = listOf(
    "Most bookmarked" to "popular",
    "Recently updated" to "updated",
    "Most viewed" to "views",
    "Longest" to "longest",
    "Trending" to "trending",
    "Top rated" to "rating",
    "Newest" to "newest",
)

private val TYPES = listOf(
    "Manhwa" to "manhwa",
    "Manhua" to "manhua",
    "Manga" to "manga",
)

private val STATUSES = listOf(
    "Ongoing" to "ongoing",
    "Completed" to "completed",
    "Hiatus" to "hiatus",
    "Dropped" to "dropped",
)

private val GENRES = listOf(
    "Action" to "action",
    "Adult" to "adult",
    "Adventure" to "adventure",
    "Comedy" to "comedy",
    "Drama" to "drama",
    "Fantasy" to "fantasy",
    "Gender Bender" to "gender-bender",
    "Harem" to "harem",
    "Historical" to "historical",
    "Horror" to "horror",
    "Josei" to "josei",
    "Martial Arts" to "martial-arts",
    "Mature" to "mature",
    "Mystery" to "mystery",
    "Psychological" to "psychological",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Seinen" to "seinen",
    "Shoujo" to "shoujo",
    "Shounen" to "shounen",
    "Shounen Ai" to "shounen-ai",
    "Slice of Life" to "slice-of-life",
    "Smut" to "smut",
    "Sports" to "sports",
    "Supernatural" to "supernatural",
    "Tragedy" to "tragedy",
    "Yaoi" to "yaoi",
)

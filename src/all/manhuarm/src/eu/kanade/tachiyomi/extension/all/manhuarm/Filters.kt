package eu.kanade.tachiyomi.extension.all.manhuarm

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

fun getFilters() = FilterList(
    SortFilter(),
    GenreFilter(),
    StatusFilter(),
    TypeFilter(),
)

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

class SortFilter :
    Filter.Select<String>("Sort by", SORTS.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        url.addQueryParameter("sort", SORTS[state].second)
    }
}

class Genre(name: String, val slug: String) : Filter.TriState(name)

class GenreFilter :
    Filter.Group<Genre>("Genres", GENRES.map { Genre(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.forEach {
            when {
                it.isIncluded() -> url.addQueryParameter("genre[]", it.slug)
                it.isExcluded() -> url.addQueryParameter("exclude_genre[]", it.slug)
            }
        }
    }
}

class CheckBoxOption(name: String, val value: String) : Filter.CheckBox(name)

open class CheckBoxGroup(name: String, private val param: String, options: List<Pair<String, String>>) :
    Filter.Group<CheckBoxOption>(name, options.map { CheckBoxOption(it.first, it.second) }),
    UrlFilter {
    override fun addToUrl(url: HttpUrl.Builder) {
        state.filter { it.state }.forEach { url.addQueryParameter(param, it.value) }
    }
}

class StatusFilter :
    CheckBoxGroup(
        "Status",
        "status[]",
        listOf(
            "OnGoing" to "on-going",
            "Completed" to "end",
            "Canceled" to "canceled",
            "On Hold" to "on-hold",
        ),
    )

class TypeFilter :
    CheckBoxGroup(
        "Type",
        "type[]",
        listOf(
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
            "Other" to "other",
        ),
    )

private val SORTS = listOf(
    "Best match" to "best_match",
    "Latest update" to "latest",
    "Recently added" to "recent",
    "Title A-Z" to "title_az",
    "Title Z-A" to "title_za",
    "Year: newest" to "year_new",
    "Year: oldest" to "year_old",
    "Highest rated" to "rated",
    "Most viewed - 1 week" to "views_week",
    "Most viewed - 1 month" to "views_month",
    "Most viewed - 3 months" to "views_quarter",
    "Most viewed - all time" to "views_all",
    "Most bookmarked" to "bookmarks",
)

private val GENRES = listOf(
    "Action" to "action",
    "Adult" to "adult",
    "Adventure" to "adventure",
    "Boys Love" to "boys-love",
    "Comedy" to "comedy",
    "Drama" to "drama",
    "Ecchi" to "ecchi",
    "Fantasy" to "fantasy",
    "Gender Bender" to "gender-bender",
    "Girls Love" to "girls-love",
    "Gourmet" to "gourmet",
    "Harem" to "harem",
    "Hentai" to "hentai",
    "Historical" to "historical",
    "Horror" to "horror",
    "Josei" to "josei",
    "Magical Girls" to "magical-girls",
    "Martial Arts" to "martial-arts",
    "Mature" to "mature",
    "Mecha" to "mecha",
    "Music" to "music",
    "Mystery" to "mystery",
    "Psychological" to "psychological",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Sci-fi" to "sci-fi",
    "Seinen" to "seinen",
    "Shoujo" to "shoujo",
    "Shounen" to "shounen",
    "Shounen Ai" to "shounen-ai",
    "Slice of Life" to "slice-of-life",
    "Smut" to "smut",
    "Sports" to "sports",
    "Supernatural" to "supernatural",
    "Thriller" to "thriller",
    "Tragedy" to "tragedy",
    "Yaoi" to "yaoi",
)

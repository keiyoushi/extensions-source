package eu.kanade.tachiyomi.extension.en.mgreadio

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

internal fun HttpUrl.Builder.addFilters(filters: FilterList) {
    filters.forEach { filter ->
        when (filter) {
            is UriFilter -> filter.addToUri(this)
            else -> {}
        }
    }
}

internal interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

internal open class UriSelectFilter(
    name: String,
    private val queryName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter(queryName, vals[state].second)
    }
}

internal class TypeFilter : UriSelectFilter("Type", "type", TYPE_OPTIONS)

internal class StatusFilter : UriSelectFilter("Status", "status", STATUS_OPTIONS)

internal class AgeRatingFilter : UriSelectFilter("Age Rating", "age_rating", AGE_RATING_OPTIONS)

internal class RatingMinFilter : UriSelectFilter("Minimum Rating", "rating_min", RATING_MIN_OPTIONS)

internal class RatingMaxFilter : UriSelectFilter("Maximum Rating", "rating_max", RATING_MAX_OPTIONS)

internal class SortFilter : UriSelectFilter("Sort By", "sort", SORT_OPTIONS)

internal class GenreFilter :
    Filter.Group<Genre>("Genres", GENRES.map { Genre(it.first, it.second) }),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach { genre ->
            builder.addQueryParameter("genre[]", genre.id)
        }
    }
}

internal class Genre(name: String, val id: String) : Filter.CheckBox(name)

private val TYPE_OPTIONS = arrayOf(
    "All Types" to "",
    "Comic" to "comic",
    "Novel" to "novel",
    "Oneshot" to "oneshot",
)

private val STATUS_OPTIONS = arrayOf(
    "All Status" to "",
    "Ongoing" to "ongoing",
    "Season End" to "season_end",
    "Completed" to "completed",
    "Source Hiatus" to "source_hiatus",
    "Caught Up" to "caught_up",
    "Dropped" to "dropped",
)

private val AGE_RATING_OPTIONS = arrayOf(
    "All Ages" to "",
    "All ages" to "all",
    "13+" to "13+",
    "16+" to "16+",
    "18+" to "18+",
)

private val RATING_MIN_OPTIONS = arrayOf(
    "Min" to "0",
    "1 star" to "1",
    "2 stars" to "2",
    "3 stars" to "3",
    "4 stars" to "4",
    "5 stars" to "5",
)

private val RATING_MAX_OPTIONS = arrayOf(
    "Max" to "6",
    "1 star" to "1",
    "2 stars" to "2",
    "3 stars" to "3",
    "4 stars" to "4",
    "5 stars" to "5",
)

private val SORT_OPTIONS = arrayOf(
    "Latest Updated" to "updated",
    "Newest" to "new",
    "Oldest" to "old",
    "Most Views" to "views",
    "Daily Views" to "views_day",
    "Weekly Views" to "views_week",
    "Monthly Views" to "views_month",
    "Highest Rating" to "rating",
    "Most Power Stone" to "power",
    "Most Followers" to "follow",
)

private val GENRES = listOf(
    "Action" to "action",
    "Adaptation" to "adaptation",
    "Adventure" to "adventure",
    "Anime" to "anime",
    "Comedy" to "comedy",
    "Cooking" to "cooking",
    "Crime" to "crime",
    "Drama" to "drama",
    "Ecchi" to "ecchi",
    "Fantasy" to "fantasy",
    "Harem" to "harem",
    "Historical" to "historical",
    "Horror" to "horror",
    "Isekai" to "isekai",
    "Josei" to "josei",
    "Martial Arts" to "martial-arts",
    "Mature" to "mature",
    "Mecha" to "mecha",
    "Medical" to "medical",
    "Music" to "music",
    "Mystery" to "mystery",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Shoujo" to "shoujo",
    "Shounen" to "shounen",
    "Slice of life" to "slice-of-life",
    "Smut" to "smut",
    "Sports" to "sports",
    "Supernatural" to "supernatural",
    "Webtoons" to "webtoons",
)

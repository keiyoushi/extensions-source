package eu.kanade.tachiyomi.multisrc.mangak

import eu.kanade.tachiyomi.source.model.Filter
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement

class Genre(name: String, val value: String, state: Int = Filter.TriState.STATE_IGNORE) : Filter.TriState(name, state)

class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

class AuthorFilter : Filter.Text("Author")
class MinChapterFilter : Filter.Text("Min Chapters")
class MaxChapterFilter : Filter.Text("Max Chapters")
class TagFilter : Filter.Text("Tag")

open class SelectFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
    val selected: String
        get() = vals.getOrNull(state)?.second ?: ""
}

class SortFilter :
    SelectFilter(
        "Sort By",
        arrayOf(
            "Best Match" to "",
            "Most Followed" to "popular",
            "Latest Updated" to "latest",
            "Recently Added" to "newest",
            "Highest Rating" to "rating",
            "Most Viewed: Today" to "views_today",
            "Most Viewed: 7 Days" to "views_7days",
            "Most Viewed: 30 Days" to "views_30days",
            "Most Viewed: All Time" to "views",
            "Most Chapters" to "chapters",
        ),
    )

class ContentRatingFilter :
    SelectFilter(
        "Content Rating",
        arrayOf(
            "Any" to "",
            "Safe" to "safe",
            "Suggestive" to "suggestive",
            "Erotica" to "erotica",
            "Pornographic" to "pornographic",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            "Any" to "",
            "Ongoing" to "ongoing",
            "Completed" to "completed",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        arrayOf(
            "Any" to "",
            "Manga" to "manga",
            "Manhwa" to "manhwa",
            "Manhua" to "manhua",
        ),
    )

class FormatFilter :
    SelectFilter(
        "Format",
        arrayOf(
            "Any" to "",
            "Oneshot" to "oneshot",
            "Adaptation" to "adaptation",
        ),
    )

class DemographicFilter :
    SelectFilter(
        "Demographics",
        arrayOf(
            "Any" to "",
            "Boy (Shounen + Seinen)" to "shounen,seinen",
            "Girl (Shoujo + Josei)" to "shoujo,josei",
            "Shounen" to "shounen",
            "Shoujo" to "shoujo",
            "Seinen" to "seinen",
            "Josei" to "josei",
        ),
    )

fun getGenreList(data: JsonElement? = null, blacklist: Set<String> = emptySet()): List<Genre> {
    val items = data?.parseAs<GenreResponseDto>()?.data?.items.orEmpty()

    return items.map { item ->
        Genre(
            name = item.name,
            value = item.slug,
            state = if (item.slug in blacklist) Filter.TriState.STATE_EXCLUDE else Filter.TriState.STATE_IGNORE,
        )
    }
}

package eu.kanade.tachiyomi.multisrc.mangak

import eu.kanade.tachiyomi.source.model.Filter
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement

class Genre(name: String, val value: String, state: Int = Filter.TriState.STATE_IGNORE) : Filter.TriState(name, state)

class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

class AuthorFilter : Filter.Text("Author")
class MinChapterFilter : Filter.Text("Min Chapters")

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
            Pair("Most Followed", "popular"),
            Pair("Best Match", ""),
            Pair("Latest Updated", "latest"),
            Pair("Recently Added", "newest"),
            Pair("Highest Rating", "rating"),
            Pair("Most Viewed: Today", "views_today"),
            Pair("Most Viewed: 7 Days", "views_7days"),
            Pair("Most Viewed: 30 Days", "views_30days"),
            Pair("Most Viewed: All Time", "views"),
            Pair("Most Chapters", "chapters"),
            Pair("A-Z", "alphabetical"),
        ),
    )

class ContentRatingFilter :
    SelectFilter(
        "Content Rating",
        arrayOf(
            Pair("Any", ""),
            Pair("Safe", "safe"),
            Pair("Suggestive", "suggestive"),
            Pair("Erotica", "erotica"),
            Pair("Pornographic", "pornographic"),
        ),
    )

class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            Pair("Any", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
            Pair("Cancelled", "cancelled"),
        ),
    )

class TypeFilter :
    SelectFilter(
        "Type",
        arrayOf(
            Pair("Any", ""),
            Pair("Manga", "manga"),
            Pair("Manhwa", "manhwa"),
            Pair("Manhua", "manhua"),
        ),
    )

class DemographicFilter :
    SelectFilter(
        "Demographics",
        arrayOf(
            Pair("Any", ""),
            Pair("Boy (Shounen + Seinen)", "shounen,seinen"),
            Pair("Girl (Shoujo + Josei)", "shoujo,josei"),
            Pair("Shounen", "shounen"),
            Pair("Shoujo", "shoujo"),
            Pair("Seinen", "seinen"),
            Pair("Josei", "josei"),
        ),
    )

fun getGenreList(data: JsonElement? = null, blacklist: Set<String> = emptySet()): List<Genre> {
    val items = data
        ?.let { runCatching { it.parseAs<GenreResponseDto>() }.getOrNull() }
        ?.genres
        .orEmpty()

    return items.map { item ->
        Genre(
            name = item.name,
            value = item.slug,
            state = if (item.slug in blacklist) Filter.TriState.STATE_EXCLUDE else Filter.TriState.STATE_IGNORE,
        )
    }
}

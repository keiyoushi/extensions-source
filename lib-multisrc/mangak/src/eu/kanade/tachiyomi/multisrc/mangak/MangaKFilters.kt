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

/**
 * On by default: a chip tap arrives as a plain query, so exact names resolve unless the
 * user opts out.
 */
class ResolveFilter : Filter.CheckBox("Treat plain searches as exact filters", true)

open class SelectFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
    val selected: String
        get() = vals.getOrNull(state)?.second ?: ""

    fun valueFor(label: String): String? = vals
        .firstOrNull { it.first.equals(label, ignoreCase = true) }
        ?.second
        ?.takeIf(String::isNotBlank)
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
            "Most Bookmarks" to "bookmarks",
            "Most Discussed" to "comments",
            "Most Reviews" to "reviews",
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

/**
 * The site's author and tag params take a lowercase slug. A space or uppercase in
 * either makes the backend ignore the filter and return the whole catalogue.
 */
internal fun String.toSlug() = lowercase().replace(SLUG_DROP_REGEX, "").trim().replace(WHITESPACE_REGEX, "-")

private val SLUG_DROP_REGEX = """[^a-z0-9\s-]""".toRegex()
private val WHITESPACE_REGEX = """\s+""".toRegex()

fun getGenreList(data: JsonElement? = null, excludedGenres: Set<String> = emptySet()): List<Genre> {
    val items = data?.parseAs<DataDto<GenreDataDto>>()?.data?.items.orEmpty()

    return items.map { item ->
        Genre(
            name = item.name,
            value = item.slug,
            state = if (item.slug in excludedGenres) Filter.TriState.STATE_EXCLUDE else Filter.TriState.STATE_IGNORE,
        )
    }
}

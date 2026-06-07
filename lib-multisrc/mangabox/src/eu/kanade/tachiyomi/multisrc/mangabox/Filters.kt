package eu.kanade.tachiyomi.multisrc.mangabox

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Order by", vals)
class StatusFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Status", vals)
class GenreFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Category", vals)

internal fun getSortFilters(): Array<Pair<String?, String>> = arrayOf(
    Pair("latest", "Latest"),
    Pair("newest", "Newest"),
    Pair("topview", "Top read"),
)

internal fun getStatusFilters(): Array<Pair<String?, String>> = arrayOf(
    Pair("all", "ALL"),
    Pair("completed", "Completed"),
    Pair("ongoing", "Ongoing"),
    Pair("drop", "Dropped"),
)

internal fun getGenreFilters(): Array<Pair<String?, String>> = arrayOf(
    Pair("all", "ALL"),
    Pair("action", "Action"),
    Pair("adult", "Adult"),
    Pair("adventure", "Adventure"),
    Pair("comedy", "Comedy"),
    Pair("cooking", "Cooking"),
    Pair("doujinshi", "Doujinshi"),
    Pair("drama", "Drama"),
    Pair("ecchi", "Ecchi"),
    Pair("fantasy", "Fantasy"),
    Pair("gender-bender", "Gender bender"),
    Pair("harem", "Harem"),
    Pair("historical", "Historical"),
    Pair("horror", "Horror"),
    Pair("isekai", "Isekai"),
    Pair("josei", "Josei"),
    Pair("manhua", "Manhua"),
    Pair("manhwa", "Manhwa"),
    Pair("martial-arts", "Martial arts"),
    Pair("mature", "Mature"),
    Pair("mecha", "Mecha"),
    Pair("medical", "Medical"),
    Pair("mystery", "Mystery"),
    Pair("one-shot", "One shot"),
    Pair("psychological", "Psychological"),
    Pair("romance", "Romance"),
    Pair("school-life", "School life"),
    Pair("sci-fi", "Sci fi"),
    Pair("seinen", "Seinen"),
    Pair("shoujo", "Shoujo"),
    Pair("shoujo-ai", "Shoujo ai"),
    Pair("shounen", "Shounen"),
    Pair("shounen-ai", "Shounen ai"),
    Pair("slice-of-life", "Slice of life"),
    Pair("smut", "Smut"),
    Pair("sports", "Sports"),
    Pair("supernatural", "Supernatural"),
    Pair("tragedy", "Tragedy"),
    Pair("webtoons", "Webtoons"),
    Pair("yaoi", "Yaoi"),
    Pair("yuri", "Yuri"),
)

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String?, String>>,
) : Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
    fun toUriPart() = vals[state].first
}

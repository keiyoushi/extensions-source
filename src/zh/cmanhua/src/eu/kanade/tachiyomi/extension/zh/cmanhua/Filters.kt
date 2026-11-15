package eu.kanade.tachiyomi.extension.zh.cmanhua

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = options[state].second
}

class SortFilter : UriPartFilter("Order by", SORT_OPTIONS)

class StatusFilter : UriPartFilter("Status", STATUS_OPTIONS)

internal val SORT_OPTIONS = arrayOf(
    Pair("Update time", "updated_desc"),
    Pair("Views", "views_desc"),
    Pair("Bookmarks", "bookmark_desc"),
    Pair("Number of chapters", "chapter_desc"),
)

internal val STATUS_OPTIONS = arrayOf(
    Pair("All", ""),
    Pair("Ongoing", "ongoing"),
    Pair("Completed", "completed"),
    Pair("Hiatus", "hiatus"),
)

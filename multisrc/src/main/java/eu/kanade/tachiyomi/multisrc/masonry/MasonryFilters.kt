package eu.kanade.tachiyomi.multisrc.masonry

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class SortFilter : SelectFilter("Sort by", sortFilterOptions) {
    fun getUriPartIfNeeded(part: String) =
        when (part) {
            "search" -> {
                when (state) {
                    2 -> ""
                    else -> selected
                }
            }
            "tag" -> {
                when (state) {
                    0 -> ""
                    else -> selected
                }
            }
            else -> ""
        }
}

private val sortFilterOptions = listOf(
    Pair("Trending", "sort/trending"),
    Pair("Newest", "sort/newest"),
    Pair("Popular", "sort/popular"),
)

class TagFilter(options: List<Pair<String, String>>) : SelectFilter("Tags", options)

package eu.kanade.tachiyomi.extension.en.webtoonhentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("Ignored with a text search"),
    TagFilter(),
)

class TagFilter : Filter.Select<String>("Tag", arrayOf("All") + TAGS.map { it.first }) {
    val slug get() = TAGS.getOrNull(state - 1)?.second
}

private val TAGS = listOf(
    "Adult" to "adult",
    "Drama" to "drama",
    "Ecchi" to "ecchi",
    "Fantasy" to "fantasy",
    "Harem" to "harem",
    "Mature" to "mature",
    "Romance" to "romance",
    "Smut" to "smut",
)

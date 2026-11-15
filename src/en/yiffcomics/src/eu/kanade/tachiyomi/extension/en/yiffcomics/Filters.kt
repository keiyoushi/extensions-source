package eu.kanade.tachiyomi.extension.en.yiffcomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class OrientationFilter : Filter.Select<String>("Orientation", arrayOf("Any") + ORIENTATIONS) {
    val selected get() = ORIENTATIONS.getOrNull(state - 1)?.lowercase()
}

class TagFilter(name: String, private val tags: List<TagDto>) : Filter.Select<String>(name, arrayOf("Any") + tags.map { it.name }) {
    val selected get() = tags.getOrNull(state - 1)?.slug
}

fun getFilters(data: TagListsDto?) = FilterList(
    listOfNotNull(
        Filter.Header("The site lists one of these at a time: text search first, then orientation, tag and character"),
        OrientationFilter(),
        data?.let { TagFilter("Tag", it.tags) },
        data?.let { TagFilter("Character", it.characters) },
        if (data == null) Filter.Header("Tap 'Reset' to load tags and characters") else null,
    ),
)

private val ORIENTATIONS = arrayOf("Straight", "Gay", "Lesbian", "Futa", "Trans")

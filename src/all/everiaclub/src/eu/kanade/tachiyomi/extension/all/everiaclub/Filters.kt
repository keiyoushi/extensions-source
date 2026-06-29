package eu.kanade.tachiyomi.extension.all.everiaclub

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val valuePair: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, valuePair.map { it.first }.toTypedArray()) {
    fun toUriPart() = valuePair[state].second
}

class CategoryFilter(categories: Array<Pair<String, String>>) : UriPartFilter("Category", categories)

class TagFilter(name: String, val id: Int) : Filter.TriState(name)

class TagGroup(tags: List<TagFilter>) : Filter.Group<TagFilter>("Tags", tags)

package eu.kanade.tachiyomi.multisrc.yiimadara

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class GenreFilter(genres: List<Pair<String, String>>) : SelectFilter("Genre", genres)

class CategoryFilter(categories: List<Pair<String, String>>) : SelectFilter("Category", categories)

class SortFilter(sorts: List<Pair<String, String>>) : SelectFilter("Sort by", sorts)

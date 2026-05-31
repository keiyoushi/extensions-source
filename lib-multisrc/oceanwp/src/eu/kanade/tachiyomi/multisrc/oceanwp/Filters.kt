package eu.kanade.tachiyomi.multisrc.oceanwp

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

class CategoryFilter(options: List<Pair<String, String>>) : SelectFilter("Category", options)
class TagFilter(options: List<Pair<String, String>>) : SelectFilter("Tag", options)

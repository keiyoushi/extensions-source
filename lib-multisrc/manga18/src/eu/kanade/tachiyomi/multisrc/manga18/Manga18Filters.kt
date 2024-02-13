package eu.kanade.tachiyomi.multisrc.manga18

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second.takeUnless { it.isEmpty() }
}

class TagFilter(tags: List<Pair<String, String>>) : SelectFilter("Tags", tags)

class SortFilter : SelectFilter("Sort", sortValues)

private val sortValues = listOf(
    Pair("Latest", ""),
    Pair("Views", "views"),
    Pair("A-Z", "name"),
)

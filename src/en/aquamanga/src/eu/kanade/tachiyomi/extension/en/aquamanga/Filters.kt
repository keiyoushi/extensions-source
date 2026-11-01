package eu.kanade.tachiyomi.extension.en.aquamanga

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
) {
    val selected get() = options[state].second.takeIf { it.isNotEmpty() }
}

class OrderByFilter(name: String, options: List<Pair<String, String>>, defaultValue: String? = null) : SelectFilter(name, options, defaultValue)
class SingleGenreSelectFilter(name: String, options: List<Pair<String, String>>, defaultValue: String? = null) : SelectFilter(name, options, defaultValue)

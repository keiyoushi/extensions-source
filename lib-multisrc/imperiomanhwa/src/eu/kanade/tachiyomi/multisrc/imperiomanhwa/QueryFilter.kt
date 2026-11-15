package eu.kanade.tachiyomi.multisrc.imperiomanhwa

import eu.kanade.tachiyomi.source.model.Filter

class QueryFilter(
    name: String,
    val param: String,
    private val options: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second.ifEmpty { null }
}

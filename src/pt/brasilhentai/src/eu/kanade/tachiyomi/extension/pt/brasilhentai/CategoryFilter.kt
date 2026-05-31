package eu.kanade.tachiyomi.extension.pt.brasilhentai

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
    vals.indexOfFirst { it.second.isEmpty() }.takeIf { it != -1 } ?: 0,
) {
    fun selectedValue() = vals[state].second
}

package eu.kanade.tachiyomi.extension.en.kunmangato

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    val internalName: String,
    private val vals: List<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
    fun toUriPart() = vals[state].first
}

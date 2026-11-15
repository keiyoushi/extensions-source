package eu.kanade.tachiyomi.multisrc.comiciviewer

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: List<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class CategoryFilter(options: List<Pair<String, String>>) : SelectFilter("Filter by", options)

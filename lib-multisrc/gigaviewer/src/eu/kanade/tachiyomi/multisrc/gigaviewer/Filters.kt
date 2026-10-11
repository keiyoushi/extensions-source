package eu.kanade.tachiyomi.multisrc.gigaviewer

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: List<Pair<String, List<String>>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: List<String>
        get() = vals[state].second
}

class CollectionFilter(options: List<Pair<String, List<String>>>) : SelectFilter("コレクション", options)

package eu.kanade.tachiyomi.extension.es.capibaratraductor

import eu.kanade.tachiyomi.source.model.Filter

class SortByFilter(title: String, list: Array<Pair<String, String>>) : UriPartFilter(title, list)

class ScanlatorFilter(title: String, list: Array<Pair<String, String>>) : UriPartFilter(title, list)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

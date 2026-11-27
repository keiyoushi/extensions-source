package eu.kanade.tachiyomi.extension.all.hotcomics

import eu.kanade.tachiyomi.source.model.Filter

class BrowseFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

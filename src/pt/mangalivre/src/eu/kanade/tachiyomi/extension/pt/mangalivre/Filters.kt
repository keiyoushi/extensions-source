package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.source.model.Filter

open class SelectedFilter(
    title: String,
    val options: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(title, options.map { it.first }.toTypedArray(), state) {
    fun selected(): String = options[state].second
}

class OrderByFilter(title: String = "", options: List<Pair<String, String>>) : SelectedFilter(title, options)

class OrderDirectionFilter(title: String = "", options: List<Pair<String, String>>) : SelectedFilter(title, options)

package eu.kanade.tachiyomi.extension.all.fsicomics

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter(private val options: List<Pair<String, String>>) : Filter.Select<String>("Category", options.map { it.first }.toTypedArray()) {
    fun toUriPart() = options[state].second
}

class TagFilter : Filter.Text("Tag (e.g. Fantasy)")

class SortFilter : Filter.Select<String>("Sort", arrayOf("Newest", "Oldest", "Relevance", "Title A-Z"))

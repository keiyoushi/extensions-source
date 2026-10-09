package eu.kanade.tachiyomi.extension.all.fsicomics

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(private val genres: List<Pair<String, String>>) : Filter.Select<String>("Genre", genres.map { it.first }.toTypedArray()) {
    fun toUriPart() = genres[state].second
}

class TagFilter : Filter.Text("Tag")

class SortFilter : Filter.Sort("Sort", arrayOf("Relevance", "Date"))

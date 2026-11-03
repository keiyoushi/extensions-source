package eu.kanade.tachiyomi.extension.en.kensscan

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.source.model.Filter

class TextFilter(name: String, val param: String) : Filter.Text(name)

class SortFilter(name: String) : Filter.Select<String>(name, SORT_OPTIONS.map { it.first }.toTypedArray()) {
    val value get() = SORT_OPTIONS[state].second
}

private val SORT_OPTIONS = listOf(
    "Relevance" to "",
    "Latest" to "latest",
    "A-Z" to "alphabet",
    "Trending" to "trending",
    "Most views" to "views",
    "New" to "new-manga",
    "Bookmarks" to "bookmarks",
)

class StatusCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class StatusList(name: String, options: List<Pair<String, String>>) : Filter.Group<StatusCheckBox>(name, options.map { StatusCheckBox(it.first, it.second) })

class GenreConditionFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

class GenreList(name: String, genres: List<GenreRoute>) : Filter.Group<GenreCheckBox>(name, genres.map { GenreCheckBox(it.name, it.slug) })

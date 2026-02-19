package eu.kanade.tachiyomi.extension.pt.sakuramangas

import eu.kanade.tachiyomi.source.model.Filter

class GenreList(title: String, genres: Array<Genre>) : Filter.Group<GenreCheckBox>(title, genres.map { GenreCheckBox(it.name, it.id) })

class GenreCheckBox(name: String, val id: String = name) : Filter.TriState(name)

class Genre(val name: String, val id: String)

open class SingleSelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    val paramKey: String,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    fun getValue(): String = options.getOrNull(state)?.second.orEmpty()
}

class DemographyFilter(
    name: String,
    options: List<Pair<String, String>>,
    paramKey: String,
) : SingleSelectFilter(name, options, paramKey)

class ClassificationFilter(
    name: String,
    options: List<Pair<String, String>>,
    paramKey: String,
) : SingleSelectFilter(name, options, paramKey)

class OrderByFilter(
    name: String,
    options: List<Pair<String, String>>,
    paramKey: String,
    state: Int = 0,
) : SingleSelectFilter(name, options, paramKey, state)

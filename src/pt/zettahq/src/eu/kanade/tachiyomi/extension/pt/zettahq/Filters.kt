package eu.kanade.tachiyomi.extension.pt.zettahq

import eu.kanade.tachiyomi.source.model.Filter

interface Sort {
    val priority: Int
}

class Genre(val name: String, val id: String = name)

class GenreCheckBox(name: String, val id: String = name) : Filter.CheckBox(name)

class GenreList(title: String, genres: List<Genre>, override val priority: Int = 0) :
    Filter.Group<GenreCheckBox>(title, genres.map { GenreCheckBox(it.name, it.id) }),
    Sort

class SelectFilter(
    title: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
    val query: String = "",
    override val priority: Int = 0,
) : Filter.Select<String>(title, vals.map { it.first }.toTypedArray(), state),
    Sort {
    fun selected() = vals[state].second
}

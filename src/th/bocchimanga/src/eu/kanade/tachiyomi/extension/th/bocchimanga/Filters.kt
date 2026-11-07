package eu.kanade.tachiyomi.extension.th.bocchimanga

import eu.kanade.tachiyomi.source.model.Filter

interface LabelFilter {
    val labels: List<String>
}

class GenreOption(name: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<String>) :
    Filter.Group<GenreOption>("หมวดหมู่", genres.map(::GenreOption)),
    LabelFilter {
    override val labels get() = state.filter { it.state }.map { it.name }
}

class LabelSelectFilter(name: String, private val options: List<String>) :
    Filter.Select<String>(name, (listOf("ทั้งหมด") + options).toTypedArray()),
    LabelFilter {
    override val labels get() = listOfNotNull(options.getOrNull(state - 1))
}

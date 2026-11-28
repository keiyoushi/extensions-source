package eu.kanade.tachiyomi.extension.all.lezhin

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter(title: String, names: Array<String>) : Filter.Select<String>(title, names) {
    val value get() = SORTS[state]

    companion object {
        val SORTS = arrayOf("POPULAR", "LATEST")
    }
}

class FreeFilter(title: String) : Filter.CheckBox(title)

class GenreCheckBox(name: String, val internalName: String) : Filter.CheckBox(name)

class GenreFilter(title: String, genres: List<GenreCheckBox>) : Filter.Group<GenreCheckBox>(title, genres) {
    val selected get() = state.filter { it.state }.map { it.internalName }
}

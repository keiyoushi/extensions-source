package eu.kanade.tachiyomi.extension.vi.minotruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class GenreFilter(genres: List<TagOption>) :
    Filter.Group<GenreCheckBox>(
        "Thể loại",
        genres.map { GenreCheckBox(it.name, it.tagId.toString()) },
    ) {
    fun selectedValues(): List<String> = state.filter { it.state }.map { it.value }
}

class GenreCheckBox(name: String, val value: String) : Filter.CheckBox(name)

fun getFilters(genres: List<TagOption>?): FilterList = if (genres.isNullOrEmpty()) {
    FilterList()
} else {
    FilterList(GenreFilter(genres))
}

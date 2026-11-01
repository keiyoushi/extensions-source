package eu.kanade.tachiyomi.extension.vi.cotruyenday

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<GenreDto>?): FilterList {
    val filters = mutableListOf<Filter<*>>()

    val options = genres?.map { Genre(it.genreName, it.genreId.toString()) }
        ?.sortedBy { it.name }
        .orEmpty()
    if (options.isNotEmpty()) {
        filters += GenreFilter("Thể loại", options)
    }
    return FilterList(filters)
}

class GenreFilter(title: String, genres: List<Genre>) : Filter.Group<Genre>(title, genres)

class Genre(name: String, val id: String) : Filter.CheckBox(name)

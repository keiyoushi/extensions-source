package eu.kanade.tachiyomi.extension.vi.thegioitruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<TheGioiTruyen.GenreDto>?): FilterList {
    val filters = mutableListOf<Filter<*>>()
    if (!genres.isNullOrEmpty()) {
        filters += GenreFilter("Thể loại", genres.sortedBy { it.name })
    }
    return FilterList(filters)
}

class GenreFilter(title: String, genres: List<TheGioiTruyen.GenreDto>) : Filter.Select<String>(title, arrayOf("Tất cả") + genres.map { it.name }.toTypedArray()) {
    private val slugs = genres.map { it.slug }

    fun toSlug(): String? = if (state == 0) null else slugs[state - 1]
}

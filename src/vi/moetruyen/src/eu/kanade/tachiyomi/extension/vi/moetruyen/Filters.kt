package eu.kanade.tachiyomi.extension.vi.moetruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<GenreOption>?): FilterList {
    val filters = mutableListOf<Filter<*>>(StatusFilter())
    if (!genres.isNullOrEmpty()) {
        filters += GenreFilter(genres.map { Genre(it.name, it.id) })
    }
    return FilterList(filters)
}

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        arrayOf("Tất cả", "Còn tiếp", "Hoàn thành", "Tạm dừng"),
    ) {
    fun toUriPart(): String? = when (state) {
        1 -> "Còn tiếp"
        2 -> "Hoàn thành"
        3 -> "Tạm dừng"
        else -> null
    }
}

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

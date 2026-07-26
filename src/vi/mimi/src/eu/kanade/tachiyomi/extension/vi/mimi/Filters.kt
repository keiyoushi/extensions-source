package eu.kanade.tachiyomi.extension.vi.mimi

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val id: Int) : Filter.TriState(name) {
    override fun toString(): String = name
}

class GenresFilter(pairs: List<GenreDto>) :
    Filter.Group<Genre>(
        "Thể loại",
        pairs.map { Genre(it.name, it.id) },
    )

class TextField(name: String, val key: String) : Filter.Text(name)

class SortByFilter :
    Filter.Select<String>(
        "Sắp xếp",
        arrayOf("Mặc định", "Mới", "Likes", "Views", "Lưu", "Tên"),
    ) {
    val sortValues = arrayOf("", "updated_at", "likes", "views", "follows", "title")
    val selectedSort get() = sortValues[state]
}

package eu.kanade.tachiyomi.extension.vi.mimi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(
    genreList: List<GenreDto>,
): FilterList = FilterList(
    SortByFilter(),
    TextField("Parody", "parody"),
    TextField("Nhân vật", "character"),
    Filter.Header("ID Tác giả (chỉ nhập số)"),
    TextField("ID Tác giả", "author"),
    if (genreList.isEmpty()) {
        Filter.Header("Nhấn 'Làm mới' để tải thể loại")
    } else {
        GenresFilter(genreList)
    },
)

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

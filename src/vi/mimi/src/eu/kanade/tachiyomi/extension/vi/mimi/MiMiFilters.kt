package eu.kanade.tachiyomi.extension.vi.mimi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genreList: List<GenreDto>): FilterList = FilterList(
    SortByFilter(),
    TextField("Tác giả", "author"),
    TextField("Parody", "parody"),
    TextField("Nhân vật", "character"),
    if (genreList.isEmpty()) {
        Filter.Header("Nhấn 'Làm mới' để tải thể loại")
    } else {
        GenresFilter(genreList)
    },
)

class Genre(name: String, val id: Int) : Filter.TriState(name)

class GenresFilter(genres: List<GenreDto>) :
    Filter.Group<Genre>(
        "Thể loại",
        genres.map { Genre(it.name, it.id) },
    )

class TextField(name: String, val key: String) : Filter.Text(name)

class SortByFilter :
    Filter.Select<String>(
        "Sắp xếp",
        arrayOf("Mới", "Likes", "Views", "Lưu", "Tên"),
    ) {
    val sortValues = arrayOf("updated_at", "likes", "views", "follows", "title")
    val selectedSort get() = sortValues[state]
}

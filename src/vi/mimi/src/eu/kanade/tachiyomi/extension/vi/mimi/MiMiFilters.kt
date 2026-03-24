package eu.kanade.tachiyomi.extension.vi.mimi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genreList: List<Pair<String, Int>>): FilterList = FilterList(
    SortByList(),
    TextField("Tác giả", "author"),
    TextField("Parody", "parody"),
    TextField("Nhân vật", "character"),
    if (genreList.isEmpty()) {
        Filter.Header("Nhấn 'Làm mới' để tải thể loại")
    } else {
        GenresFilter(genreList)
    },
)

class Genre(name: String, val id: String) : Filter.TriState(name) {
    override fun toString(): String = name
}

class GenresFilter(pairs: List<Pair<String, Int>>) :
    Filter.Group<Genre>(
        "Thể loại",
        pairs.map { Genre(it.first, it.second.toString()) },
    )

class TextField(name: String, val key: String) : Filter.Text(name)

class SortByList :
    Filter.Select<Genre>(
        "Sắp xếp",
        arrayOf(
            Genre("Mới", "updated_at"),
            Genre("Likes", "likes"),
            Genre("Views", "views"),
            Genre("Lưu", "follows"),
            Genre("Tên", "title"),
        ),
    )

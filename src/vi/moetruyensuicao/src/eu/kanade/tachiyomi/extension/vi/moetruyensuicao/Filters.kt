package eu.kanade.tachiyomi.extension.vi.moetruyensuicao

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(genres: List<GenreItem>) :
    Filter.Group<GenreTriStateFilter>(
        "Thể loại",
        genres.map { GenreTriStateFilter(it.name, it.id) },
    )

class GenreTriStateFilter(name: String, val genreId: Int) : Filter.TriState(name)

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        arrayOf(
            "Tất cả",
            "Đang cập nhật",
            "Hoàn thành",
            "Tạm ngưng",
            "Đã hủy",
        ),
    ) {
    fun toApiValue(): String? = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        3 -> "hiatus"
        4 -> "cancelled"
        else -> null
    }
}

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp",
        arrayOf(
            "Mới nhất",
            "A → Z",
            "Phổ biến",
        ),
    ) {
    fun toApiValue(): String = when (state) {
        1 -> "title"
        2 -> "popular"
        else -> "updated_at"
    }
}

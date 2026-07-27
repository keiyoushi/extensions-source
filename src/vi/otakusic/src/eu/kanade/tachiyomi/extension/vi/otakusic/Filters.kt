package eu.kanade.tachiyomi.extension.vi.otakusic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        add(SortFilter())
        add(StatusFilter())
        genres?.takeIf { it.isNotEmpty() }?.let { add(GenreFilter(it)) }
    },
)

@Serializable
class GenreOption(
    val name: String,
    val value: String,
)

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp",
        arrayOf("Mới cập nhật", "Lượt xem", "Tên A-Z"),
    ) {
    fun toUriPart() = when (state) {
        1 -> "views"
        2 -> "name"
        else -> "updated"
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        arrayOf("Tất cả", "Đang tiến hành", "Đã hoàn thành"),
    ) {
    fun toUriPart(): String? = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        else -> null
    }
}

class GenreFilter(genres: List<GenreOption>) :
    Filter.Select<String>(
        "Thể loại",
        arrayOf("Tất cả") + genres.map { it.name }.toTypedArray(),
    ) {
    private val genreValues = genres.map { it.value }

    fun toUriPart(): String? = if (state == 0) null else genreValues[state - 1]
}

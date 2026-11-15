package eu.kanade.tachiyomi.extension.vi.vietmanhwa

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<GenreDto>): FilterList = FilterList(
    listOfNotNull(
        SortFilter(),
        StatusFilter(),
        GenreFilter(genres).takeIf { genres.isNotEmpty() },
    ),
)

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp theo",
        SORTS.map { it.first }.toTypedArray(),
        2,
    ) {
    val value get() = SORTS[state].second

    companion object {
        private val SORTS = listOf(
            "Mới cập nhật" to "updatedAt",
            "Cũ nhất" to "oldest",
            "Đọc nhiều" to "viewNumber",
            "Đánh giá cao" to "likeNumber",
            "Đã hoàn thành" to "completed",
        )
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Tình trạng",
        STATUSES.map { it.first }.toTypedArray(),
    ) {
    val value get() = STATUSES[state].second

    companion object {
        private val STATUSES = listOf(
            "Tất cả" to "",
            "Đang tiến hành" to "ongoing",
            "Đã hoàn thành" to "completed",
            "Oneshot" to "oneshot",
        )
    }
}

class GenreTriState(name: String, val slug: String) : Filter.TriState(name)

class GenreFilter(genres: List<GenreDto>) :
    Filter.Group<GenreTriState>(
        "Thể loại",
        genres.map { GenreTriState(it.name, it.slug) },
    )

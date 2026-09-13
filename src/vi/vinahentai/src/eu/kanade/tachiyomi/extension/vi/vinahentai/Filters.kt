package eu.kanade.tachiyomi.extension.vi.vinahentai

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
internal class GenreOption(
    val name: String,
    val slug: String,
)

internal class GenreFilter(private val genres: List<GenreOption>) :
    Filter.Select<String>(
        "Thể loại",
        arrayOf("Tất cả", *genres.map { it.name }.toTypedArray()),
    ) {
    val selected get() = if (state == 0) null else genres.getOrNull(state - 1)?.slug
}

internal class SortFilter :
    Filter.Select<String>(
        "Sắp xếp theo",
        arrayOf("Mới cập nhật", "Xem nhiều", "Đánh giá cao", "Cũ nhất"),
    ) {
    fun toUriPart() = when (state) {
        1 -> "views"
        2 -> "likes"
        3 -> "oldest"
        else -> "updatedAt"
    }
}

internal class StatusFilter :
    Filter.Select<String>(
        "Tình trạng",
        arrayOf("Tất cả", "Đang tiến hành", "Đã hoàn thành"),
    ) {
    fun toUriPart() = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        else -> ""
    }
}

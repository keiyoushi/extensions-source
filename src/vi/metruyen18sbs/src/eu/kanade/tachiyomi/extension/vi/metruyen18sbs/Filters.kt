package eu.kanade.tachiyomi.extension.vi.metruyen18sbs

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

class SortFilter : SelectFilter("Sắp xếp", SORTS, 1)

class PeriodFilter : SelectFilter("Thời gian", PERIODS)

class FormatFilter : SelectFilter("Định dạng", FORMATS)

class StatusFilter : SelectFilter("Trạng thái", STATUSES)

class GenreFilter(genres: List<GenreDto>) : SelectFilter("Thể loại", listOf("Tất cả thể loại" to "") + genres.map { it.name to it.value })

fun getFilters(genres: List<GenreDto>?) = FilterList(
    listOfNotNull(
        SortFilter(),
        PeriodFilter(),
        FormatFilter(),
        StatusFilter(),
        genres?.let(::GenreFilter),
    ),
)

private val SORTS = listOf(
    "Mới cập nhật" to "updated",
    "Đọc nhiều" to "popular",
    "Đánh giá cao" to "rating",
)

private val PERIODS = listOf(
    "Tất cả thời gian" to "",
    "Hôm nay" to "day",
    "Tuần này" to "week",
    "Tháng này" to "month",
)

private val FORMATS = listOf(
    "Tất cả" to "",
    "Manga" to "manga",
    "Manhwa" to "manhwa",
    "Manhua" to "manhua",
    "Comic" to "comic",
)

private val STATUSES = listOf(
    "Tất cả" to "",
    "Đang tiến hành" to "ongoing",
    "Hoàn thành" to "completed",
    "Tạm ngưng" to "hiatus",
)

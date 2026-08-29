package eu.kanade.tachiyomi.extension.vi.fastscan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>? = null): FilterList {
    val filters = mutableListOf<Filter<*>>()
    if (!genres.isNullOrEmpty()) filters += GenreFilter(genres)
    filters += MinChapterFilter()
    filters += StatusFilter()
    filters += SortFilter()
    return FilterList(filters)
}

class GenreFilter(genres: List<GenreOption>) :
    Filter.Select<String>(
        "Thể loại truyện",
        arrayOf("Tất cả") + genres.map { genre -> genre.name }.toTypedArray(),
    ) {
    private val genres = genres

    val selected: String?
        get() = genres.getOrNull(state - 1)?.id
}

class MinChapterFilter :
    Filter.Select<String>(
        "Số lượng chương",
        MIN_CHAPTER_OPTIONS.map { option -> option.name }.toTypedArray(),
    ) {
    val value: String
        get() = MIN_CHAPTER_OPTIONS[state].value
}

class StatusFilter :
    Filter.Select<String>(
        "Tình trạng",
        STATUS_OPTIONS.map { option -> option.name }.toTypedArray(),
    ) {
    val value: String
        get() = STATUS_OPTIONS[state].value
}

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp",
        SORT_OPTIONS.map { option -> option.name }.toTypedArray(),
    ) {
    val value: String
        get() = SORT_OPTIONS[state].value
}

@Serializable
class GenreOption(val id: String, val name: String)

private class SearchOption(val value: String, val name: String)

private val MIN_CHAPTER_OPTIONS = listOf(
    SearchOption("0", "> 0"),
    SearchOption("50", ">= 50"),
    SearchOption("100", ">= 100"),
    SearchOption("200", ">= 200"),
    SearchOption("300", ">= 300"),
    SearchOption("400", ">= 400"),
    SearchOption("500", ">= 500"),
)

private val STATUS_OPTIONS = listOf(
    SearchOption("0", "Tất cả"),
    SearchOption("1", "Đang tiến hành"),
    SearchOption("2", "Hoàn thành"),
)

private val SORT_OPTIONS = listOf(
    SearchOption("0", "Ngày đăng giảm dần"),
    SearchOption("1", "Ngày đăng tăng dần"),
    SearchOption("2", "Ngày cập nhật giảm dần"),
    SearchOption("3", "Ngày cập nhật tăng dần"),
    SearchOption("4", "Lượt xem giảm dần"),
    SearchOption("5", "Lượt xem tăng dần"),
)

package eu.kanade.tachiyomi.extension.vi.fastscan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    GenreFilter(),
    MinChapterFilter(),
    StatusFilter(),
    SortFilter(),
)

class GenreFilter :
    Filter.Select<String>(
        "Thể loại truyện",
        arrayOf("Tất cả") + GENRES.map { genre -> genre.name }.toTypedArray(),
    ) {
    val selected: String?
        get() = GENRES.getOrNull(state - 1)?.id
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

private class GenreOption(val id: String, val name: String)
private class SearchOption(val value: String, val name: String)

private val GENRES = listOf(
    GenreOption("1", "Action"),
    GenreOption("2", "Comedy"),
    GenreOption("3", "Manga"),
    GenreOption("4", "School Life"),
    GenreOption("5", "Shounen"),
    GenreOption("6", "Manhwa"),
    GenreOption("7", "Ngôn Tình"),
    GenreOption("8", "Romance"),
    GenreOption("9", "Truyện Màu"),
    GenreOption("10", "Webtoon"),
    GenreOption("11", "Xuyên Không"),
    GenreOption("12", "Manhua"),
    GenreOption("13", "Mystery"),
    GenreOption("14", "Sci-fi"),
    GenreOption("15", "Đam Mỹ"),
    GenreOption("16", "Gender Bender"),
    GenreOption("17", "Seinen"),
    GenreOption("18", "Slice of Life"),
    GenreOption("19", "Comic"),
    GenreOption("20", "Fantasy"),
    GenreOption("21", "Supernatural"),
    GenreOption("22", "Chuyển Sinh"),
    GenreOption("23", "Cổ Đại"),
    GenreOption("24", "Adventure"),
    GenreOption("25", "Martial Arts"),
    GenreOption("26", "Ecchi"),
    GenreOption("27", "Horror"),
    GenreOption("28", "Psychological"),
    GenreOption("29", "Smut"),
    GenreOption("30", "Harem"),
    GenreOption("31", "Mature"),
    GenreOption("32", "Historical"),
    GenreOption("33", "Drama"),
    GenreOption("34", "Trọng Sinh"),
    GenreOption("35", "Shoujo"),
    GenreOption("36", "Josei"),
    GenreOption("37", "Adult"),
    GenreOption("38", "One Shot"),
    GenreOption("39", "Tragedy"),
    GenreOption("40", "Huyền Huyễn"),
    GenreOption("41", "Anime"),
    GenreOption("42", "Yuri"),
    GenreOption("43", "Isekai"),
    GenreOption("44", "Sports"),
    GenreOption("45", "Doujinshi"),
    GenreOption("46", "Yaoi"),
    GenreOption("47", "Hệ Thống"),
    GenreOption("48", "Soft Yaoi"),
    GenreOption("49", "BoyLove"),
    GenreOption("50", "Detective"),
    GenreOption("51", "Magic"),
    GenreOption("52", "Mafia"),
    GenreOption("53", "Mecha"),
    GenreOption("54", "Trinh Thám"),
    GenreOption("55", "Shounen Ai"),
    GenreOption("56", "Demons"),
    GenreOption("57", "18+"),
    GenreOption("58", "Soft Yuri"),
    GenreOption("59", "Shoujo Ai"),
    GenreOption("60", "16+"),
    GenreOption("61", "Live action"),
    GenreOption("62", "Military"),
)

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

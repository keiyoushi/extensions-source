package eu.kanade.tachiyomi.extension.vi.zettruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    SortFilter(),
    StatusFilter(),
    TypeFilter(),
    GenreFilter(getGenres()),
)

class SortFilter : Filter.Select<String>(
    "Sắp xếp",
    arrayOf("Mới cập nhật", "Xếp hạng", "Số lượng bookmark", "Tên A-Z", "Tên Z-A"),
) {
    fun toUriPart(): String = when (state) {
        1 -> "rating"
        2 -> "bookmark"
        3 -> "name_asc"
        4 -> "name_desc"
        else -> "latest"
    }
}

class StatusFilter : Filter.Select<String>(
    "Trạng thái",
    arrayOf("Tất cả", "Đang tiến hành", "Hoàn thành"),
) {
    fun toUriPart(): String = when (state) {
        1 -> "Đang"
        2 -> "Hoàn thành"
        else -> "all"
    }
}

class TypeFilter : Filter.Select<String>(
    "Loại truyện",
    arrayOf("Tất cả", "Manga", "Manhua", "Manhwa"),
) {
    fun toUriPart(): String = when (state) {
        1 -> "manga"
        2 -> "manhua"
        3 -> "manhwa"
        else -> "all"
    }
}

class Genre(name: String, val id: String) : Filter.CheckBox(name, false)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

private fun getGenres() = listOf(
    Genre("Action", "action"),
    Genre("Adventure", "adventure"),
    Genre("Anime", "anime"),
    Genre("Chuyển Sinh", "chuyen-sinh"),
    Genre("Cổ Đại", "co-dai"),
    Genre("Comedy", "comedy"),
    Genre("Comic", "comic"),
    Genre("Cooking", "cooking"),
    Genre("Doujinshi", "doujinshi"),
    Genre("Drama", "drama"),
    Genre("Fantasy", "fantasy"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Live action", "live-action"),
    Genre("Manga", "manga"),
    Genre("Manhua", "manhua"),
    Genre("Manhwa", "manhwa"),
    Genre("Martial Arts", "martial-arts"),
    Genre("Mecha", "mecha"),
    Genre("Mystery", "mystery"),
    Genre("Ngôn Tình", "ngon-tinh"),
    Genre("Psychological", "psychological"),
    Genre("Romance", "romance"),
    Genre("School Life", "school-life"),
    Genre("Sci-fi", "sci-fi"),
    Genre("Shoujo", "shoujo"),
    Genre("Shoujo Ai", "shoujo-ai"),
    Genre("Shounen", "shounen"),
    Genre("Shounen Ai", "shounen-ai"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Sports", "sports"),
    Genre("Supernatural", "supernatural"),
    Genre("Thiếu Nhi", "thieu-nhi"),
    Genre("Tragedy", "tragedy"),
    Genre("Trinh Thám", "trinh-tham"),
    Genre("Truyện Màu", "truyen-mau"),
    Genre("Truyện scan", "truyen-scan"),
    Genre("Tu Tiên", "tu-tien"),
    Genre("Webtoon", "webtoon"),
    Genre("Xuyên Không", "xuyen-khong"),
    Genre("Đam Mỹ", "dam-my"),
)

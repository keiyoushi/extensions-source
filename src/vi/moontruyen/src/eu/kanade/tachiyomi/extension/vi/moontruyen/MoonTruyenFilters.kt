package eu.kanade.tachiyomi.extension.vi.moontruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    GenreFilter(),
)

class GenreFilter :
    Filter.Select<String>(
        "Thể loại",
        GENRES.map { it.first }.toTypedArray(),
    ) {
    fun toUriPart(): String? = GENRES[state].second
}

private val GENRES = listOf(
    "Tất cả" to null,
    "Action" to "action",
    "Adventure" to "adventure",
    "Anime" to "anime",
    "Comedy" to "comedy",
    "Comic" to "comic",
    "Chuyển Sinh" to "chuyen-sinh",
    "Cooking" to "cooking",
    "Cổ Đại" to "co-dai",
    "Doujinshi" to "doujinshi",
    "Drama" to "drama",
    "Fantasy" to "fantasy",
    "Harem" to "harem",
    "Gender Bender" to "gender-bender",
    "Historical" to "historical",
    "Josei" to "josei",
    "Live Action" to "live-action",
    "Manga" to "manga",
    "Manhua" to "manhua",
    "Manhwa" to "manhwa",
    "Martial Arts" to "martial-arts",
    "Mature" to "mature",
    "Mystery" to "mystery",
    "Ngôn Tình" to "ngon-tinh",
    "One Shot" to "one-shot",
    "Psychological" to "psychological",
    "Romance" to "romance",
    "Sci-fi" to "sci-fi",
    "Shoujo" to "shoujo",
    "Shounen" to "shounen",
    "Slice of Life" to "slice-of-life",
    "Sports" to "sports",
    "Webtoon" to "webtoon",
    "Tạp Chí Truyện Tranh" to "tap-chi-truyen-tranh",
    "Trinh Thám" to "trinh-tham",
    "Truyện Màu" to "truyen-mau",
    "School Life" to "school-life",
    "Seinen" to "seinen",
    "Supernatural" to "supernatural",
    "Thiếu Nhi" to "thieu-nhi",
    "Tragedy" to "tragedy",
    "Truyện scan" to "truyen-scan",
    "Xuyên Không" to "xuyen-khong",
    "Mecha" to "mecha",
    "Việt Nam" to "viet-nam",
    "Truyện chữ" to "truyen-chu",
)

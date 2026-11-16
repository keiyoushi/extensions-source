package eu.kanade.tachiyomi.extension.vi.ungtycomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    SortFilter(),
    GenreFilter(),
)

open class SelectFilter(name: String, private val options: List<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "Sắp xếp",
        listOf(
            "Mới cập nhật" to "updated_at",
            "Top View" to "views",
            "Truyện mới" to "new",
        ),
        state = 1,
    )

class GenreFilter :
    SelectFilter(
        "Thể loại",
        listOf(
            "Tất cả" to "",
            "Học Đường" to "hoc-duong",
            "Lãng Mạn" to "lang-man",
            "School Life" to "school-life",
            "Romance" to "romance",
            "Yaoi (BL)" to "yaoi-bl",
            "Linh Dị" to "linh-di",
            "Xuyên Không" to "xuyen-khong",
            "Ngược" to "nguoc",
            "ABO" to "abo",
            "Drama" to "drama",
            "Giới giải trí" to "gioi-giai-tri",
            "Hài Hước" to "hai-huoc",
            "19+" to "19",
            "Manhwa" to "manhwa",
            "Sủng" to "sung",
            "Hiện Thực" to "hien-thuc",
            "Full Trọn Bộ" to "full-tron-bo",
            "eSports" to "esports",
            "Niên Hạ" to "nien-ha",
            "Sư Đồ Luyến" to "su-do-luyen",
            "Trọng Sinh" to "trong-sinh",
            "Báo Thù" to "bao-thu",
            "Tổng Tài" to "tong-tai",
            "Huyết Tộc" to "huyet-toc",
            "Võng Du" to "vong-du",
            "Hắc Bang" to "hac-bang",
            "Tu Tiên" to "tu-tien",
            "Ngôn Tình" to "ngon-tinh",
            "Truyện Màu" to "truyen-mau",
            "Shoujo" to "shoujo",
            "Action" to "action",
            "Fantasy" to "fantasy",
            "Gender Bender" to "gender-bender",
            "Shoujo Ai" to "shoujo-ai",
            "Adventure" to "adventure",
            "Comedy" to "comedy",
            "Manga" to "manga",
            "Slice of Life" to "slice-of-life",
            "Historical" to "historical",
            "Martial Arts" to "martial-arts",
            "Webtoon" to "webtoon",
            "Chuyển Sinh" to "chuyen-sinh",
            "Horror" to "horror",
            "Shounen" to "shounen",
            "Supernatural" to "supernatural",
            "Psychological" to "psychological",
            "Mystery" to "mystery",
            "Sports" to "sports",
            "Cổ Đại" to "co-dai",
            "Sci-fi" to "sci-fi",
            "Comic" to "comic",
            "Boys Love" to "boys-love",
            "Đam Mỹ" to "dam-my",
            "BL" to "bl",
            "Cổ Trang" to "co-trang",
            "Manhua" to "manhua",
            "Hệ Thống" to "he-thong",
            "Hiện Đại" to "hien-dai",
            "Xuyên Nhanh" to "xuyen-nhanh",
        ),
    )

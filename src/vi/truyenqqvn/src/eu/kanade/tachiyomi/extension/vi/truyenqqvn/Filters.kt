package eu.kanade.tachiyomi.extension.vi.truyenqqvn

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(name: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val selected get() = options[state].second
}

class ListFilter :
    UriPartFilter(
        "Danh sách",
        arrayOf(
            "Truyện hot" to "/truyen-hot",
            "Truyện mới" to "/truyen-moi",
            "Truyện full" to "/truyen-full",
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Thể loại",
        arrayOf(
            "Tất cả" to "",
            "Action" to "action",
            "Adult" to "adult",
            "Adventure" to "adventure",
            "Anime" to "anime",
            "Chuyển Sinh" to "chuyen-sinh",
            "Comedy" to "comedy",
            "Comic" to "comic",
            "Cooking" to "cooking",
            "Cổ Đại" to "co-dai",
            "Demons" to "demons",
            "Detective" to "detective",
            "Doujinshi" to "doujinshi",
            "Drama" to "drama",
            "Ecchi" to "ecchi",
            "Fantasy" to "fantasy",
            "Gender Bender" to "gender-bender",
            "Harem" to "harem",
            "Historical" to "historical",
            "Horror" to "horror",
            "Huyền Huyễn" to "huyen-huyen",
            "Isekai" to "isekai",
            "Josei" to "josei",
            "Khác" to "khac",
            "Live action" to "live-action",
            "Magic" to "magic",
            "Manga" to "manga",
            "Manhua" to "manhua",
            "Manhwa" to "manhwa",
            "Martial Arts" to "martial-arts",
            "Mature" to "mature",
            "Mecha" to "mecha",
            "Mystery" to "mystery",
            "Ngôn Tình" to "ngon-tinh",
            "One shot" to "one-shot",
            "Psychological" to "psychological",
            "Romance" to "romance",
            "School Life" to "school-life",
            "Sci-fi" to "sci-fi",
            "Seinen" to "seinen",
            "Shoujo" to "shoujo",
            "Shoujo Ai" to "shoujo-ai",
            "Shounen" to "shounen",
            "Shounen Ai" to "shounen-ai",
            "Slice of Life" to "slice-of-life",
            "Smut" to "smut",
            "Soft Yaoi" to "soft-yaoi",
            "Soft Yuri" to "soft-yuri",
            "Sports" to "sports",
            "Supernatural" to "supernatural",
            "Thiếu Nhi" to "thieu-nhi",
            "Tragedy" to "tragedy",
            "Trinh Thám" to "trinh-tham",
            "Trọng Sinh" to "trong-sinh",
            "Truyện Màu" to "truyen-mau",
            "Webtoon" to "webtoon",
            "Xuyên Không" to "xuyen-khong",
            "Yaoi" to "yaoi",
            "Yuri" to "yuri",
            "Đam Mỹ" to "dam-my",
        ),
    )

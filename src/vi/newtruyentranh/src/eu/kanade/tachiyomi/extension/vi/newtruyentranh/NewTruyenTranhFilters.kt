package eu.kanade.tachiyomi.extension.vi.newtruyentranh

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter :
    UriPartFilter(
        "Thể loại",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Âu Cổ", "au-co"),
            Pair("Chuyển Sinh", "chuyen-sinh"),
            Pair("Cổ Đại", "co-dai"),
            Pair("Comedy", "comedy"),
            Pair("Comic", "comic"),
            Pair("Detective", "detective"),
            Pair("Đô Thị", "do-thi"),
            Pair("Đời Thường", "doi-thuong"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Hài Hước", "hai-huoc"),
            Pair("Hành động", "hanh-dong"),
            Pair("Harem", "harem"),
            Pair("Hậu Cung", "hau-cung"),
            Pair("Hệ Thống", "he-thong"),
            Pair("Hiện đại", "hien-dai"),
            Pair("Historical", "historical"),
            Pair("Học đường", "hoc-duong"),
            Pair("Horror", "horror"),
            Pair("Huyền Huyễn", "huyen-huyen"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Kinh Dị", "kinh-di"),
            Pair("Magic", "magic"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mạt Thế", "mat-the"),
            Pair("Mature", "mature"),
            Pair("Mystery", "mystery"),
            Pair("Ngôn Tình", "ngon-tinh"),
            Pair("One shot", "one-shot"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragedy", "tragedy"),
            Pair("Trọng Sinh", "trong-sinh"),
            Pair("Trùng Sinh", "trung-sinh"),
            Pair("Truyện Chill", "truyen-chill"),
            Pair("Truyện Đánh đấm", "truyen-danh-dam"),
            Pair("Truyện Học viện", "truyen-hoc-vien"),
            Pair("Truyện Manga", "truyen-manga"),
            Pair("Truyện Manga màu", "truyen-manga-mau"),
            Pair("Truyện Màu", "truyen-mau"),
            Pair("Truyện Sci-fi", "truyen-sci-fi"),
            Pair("Truyện Siêu năng lực", "truyen-sieu-nang-luc"),
            Pair("Truyện Thể thao", "truyen-the-thao"),
            Pair("Truyện Võ lâm", "truyen-vo-lam"),
            Pair("Truyện Xã hội đen", "truyen-xa-hoi-den"),
            Pair("Tu Tiên", "tu-tien"),
            Pair("Webtoon", "webtoon"),
            Pair("Xuyên Không", "xuyen-khong"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Xếp hạng",
        arrayOf(
            Pair("Mặc định", ""),
            Pair("Top all", "10"),
            Pair("Top tháng", "11"),
            Pair("Top tuần", "12"),
            Pair("Top ngày", "13"),
            Pair("Truyện mới", "15"),
            Pair("Số chương", "30"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Đang tiến hành", "1"),
            Pair("Đã hoàn thành", "2"),
        ),
    )

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

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
            Pair("Chill", "chill"),
            Pair("Chuyển Sinh", "chuyen-sinh"),
            Pair("Cổ Đại", "co-dai"),
            Pair("Comedy", "comedy"),
            Pair("Comic", "comic"),
            Pair("Đánh Đấm", "danh-dam"),
            Pair("Đang Cập Nhật", "dang-cap-nhat"),
            Pair("Detective", "detective"),
            Pair("Đô Thị", "do-thi"),
            Pair("Đời Thường", "doi-thuong"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Hài Hước", "hai-huoc"),
            Pair("Hành Động", "hanh-dong"),
            Pair("Harem", "harem"),
            Pair("Hậu Cung", "hau-cung"),
            Pair("Hệ Thống", "he-thong"),
            Pair("Hiện Đại", "hien-dai"),
            Pair("Historical", "historical"),
            Pair("Học Đường", "hoc-duong"),
            Pair("Học Viện", "hoc-vien"),
            Pair("Horror", "horror"),
            Pair("HOT", "hot"),
            Pair("Huyền Huyễn", "huyen-huyen"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Kinh Dị", "kinh-di"),
            Pair("Magic", "magic"),
            Pair("Manga", "manga"),
            Pair("Manga Màu", "manga-mau"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mạt Thế", "mat-the"),
            Pair("Mature", "mature"),
            Pair("Mystery", "mystery"),
            Pair("Ngôn Tình", "ngon-tinh"),
            Pair("One Shot", "one-shot"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Siêu Năng Lực", "sieu-nang-luc"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Thể Thao", "the-thao"),
            Pair("Tragedy", "tragedy"),
            Pair("Trọng Sinh", "trong-sinh"),
            Pair("Trùng Sinh", "trung-sinh"),
            Pair("Truyện Màu", "truyen-mau"),
            Pair("Tu Tiên", "tu-tien"),
            Pair("Võ Lâm", "vo-lam"),
            Pair("Webtoon", "webtoon"),
            Pair("Xã Hội Đen", "xa-hoi-den"),
            Pair("Xuyên Không", "xuyen-khong"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            Pair("Mặc định", "0"),
            Pair("Top All", "10"),
            Pair("Top Tháng", "11"),
            Pair("Top Tuần", "12"),
            Pair("Top Ngày", "13"),
            Pair("Truyện Mới", "15"),
            Pair("Yêu Thích", "20"),
            Pair("Bình Luận", "25"),
            Pair("Số Chapter", "30"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            Pair("Tất cả", "-1"),
            Pair("Đang tiến hành", "1"),
            Pair("Đã hoàn thành", "2"),
        ),
    )

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

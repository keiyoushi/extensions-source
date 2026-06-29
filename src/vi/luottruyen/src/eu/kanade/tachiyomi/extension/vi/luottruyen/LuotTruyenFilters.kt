package eu.kanade.tachiyomi.extension.vi.luottruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("Lưu ý: Bộ lọc không dùng chung được với tìm kiếm"),
    Filter.Separator(),
    GenreFilter(),
    SortFilter(),
    StatusFilter(),
)

class GenreFilter :
    UriPartFilter(
        "Thể loại",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Anime", "anime"),
            Pair("Chuyển Sinh", "chuyen-sinh"),
            Pair("Chill", "chill"),
            Pair("Cổ Đại", "co-dai"),
            Pair("Comedy", "comedy"),
            Pair("Đánh đấm", "danh-dam"),
            Pair("Đô Thị", "do-thi"),
            Pair("Đời Thường", "doi-thuong"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Hài Hước", "hai-huoc"),
            Pair("Hành động", "hanh-dong"),
            Pair("Harem", "harem"),
            Pair("Hậu Cung", "hau-cung"),
            Pair("Hệ Thống", "he-thong"),
            Pair("Hiện đại", "hien-dai"),
            Pair("Học đường", "hoc-duong"),
            Pair("Học viện", "hoc-vien"),
            Pair("Horror", "horror"),
            Pair("HOT", "hot"),
            Pair("Huyền Huyễn", "huyen-huyen"),
            Pair("Kinh Dị", "kinh-di"),
            Pair("Manga", "manga"),
            Pair("Manga màu", "manga-mau"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mạt Thế", "mat-the"),
            Pair("Mystery", "mystery"),
            Pair("Nữ Cường", "nu-cuong"),
            Pair("Âu Cổ", "au-co"),
            Pair("Phiêu Lưu", "phieu-luu"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Siêu năng lực", "sieu-nang-luc"),
            Pair("Siêu Nhiên", "sieu-nhien"),
            Pair("Supernatural", "supernatural"),
            Pair("Tổng Tài", "tong-tai"),
            Pair("Trong Sinh", "trong-sinh"),
            Pair("Trùng Sinh", "trung-sinh"),
            Pair("Truyện Màu", "truyen-mau"),
            Pair("Tu Tiên", "tu-tien"),
            Pair("Võ lâm", "vo-lam"),
            Pair("Webtoon", "webtoon"),
            Pair("Xã hội đen", "xa-hoi-den"),
            Pair("Xuyên Không", "xuyen-khong"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
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
            Pair("Tất cả", "-1"),
            Pair("Đang tiến hành", "1"),
            Pair("Đã hoàn thành", "2"),
        ),
    )

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart(): String? = vals[state].second.ifEmpty { null }
}

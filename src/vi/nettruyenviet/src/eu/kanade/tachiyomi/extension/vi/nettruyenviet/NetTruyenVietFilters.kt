package eu.kanade.tachiyomi.extension.vi.nettruyenviet

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(
    genres: Array<Pair<String, String?>>,
    websites: Array<Pair<String, String?>>,
): FilterList = FilterList(
    Filter.Header("Lưu ý: Bộ lọc không dùng chung được với tìm kiếm"),
    Filter.Separator(),
    GenreFilter(genres),
    WebsiteFilter(websites),
    SortFilter(),
    StatusFilter(),
)

class GenreFilter(options: Array<Pair<String, String?>>) : UriPartFilter("Thể loại", options)

class WebsiteFilter(options: Array<Pair<String, String?>>) : UriPartFilter("Websiste", options)

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            Pair("Ngày cập nhật", null),
            Pair("Truyện mới", "15"),
            Pair("Top all", "10"),
            Pair("Top tháng", "11"),
            Pair("Top tuần", "12"),
            Pair("Top ngày", "13"),
            Pair("Theo dõi", "20"),
            Pair("Bình luận", "25"),
            Pair("Số chapter", "30"),
            Pair("Top Follow", "19"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            Pair("Tất cả", null),
            Pair("Đang tiến hành", "1"),
            Pair("Hoàn thành", "2"),
        ),
    )

open class UriPartFilter(displayName: String, private val options: Array<Pair<String, String?>>) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    fun toUriPart(): String? = options[state].second
}

val GenreFilterOptions: Array<Pair<String, String?>> = arrayOf(
    Pair("Tất cả", null),
    Pair("Action", "/tim-truyen/action-95"),
    Pair("Adventure", "/tim-truyen/adventure"),
    Pair("Anime", "/tim-truyen/anime"),
    Pair("Chuyển Sinh", "/tim-truyen/chuyen-sinh-2130"),
    Pair("Comedy", "/tim-truyen/comedy-99"),
    Pair("Comic", "/tim-truyen/comic"),
    Pair("Cooking", "/tim-truyen/cooking"),
    Pair("Cổ Đại", "/tim-truyen/co-dai-207"),
    Pair("Doujinshi", "/tim-truyen/doujinshi"),
    Pair("Drama", "/tim-truyen/drama-103"),
    Pair("Đam Mỹ", "/tim-truyen/dam-my"),
    Pair("Fantasy", "/tim-truyen/fantasy-105"),
    Pair("Gender Bender", "/tim-truyen/gender-bender"),
    Pair("Historical", "/tim-truyen/historical"),
    Pair("Horror", "/tim-truyen/horror"),
    Pair("Live action", "/tim-truyen/live-action"),
    Pair("Manga", "/tim-truyen/manga-112"),
    Pair("Manhua", "/tim-truyen/manhua"),
    Pair("Manhwa", "/tim-truyen/manhwa-11400"),
    Pair("Martial Arts", "/tim-truyen/martial-arts"),
    Pair("Mecha", "/tim-truyen/mecha-117"),
    Pair("Mystery", "/tim-truyen/mystery"),
    Pair("Ngôn Tình", "/tim-truyen/ngon-tinh"),
    Pair("Psychological", "/tim-truyen/psychological"),
    Pair("Romance", "/tim-truyen/romance-121"),
    Pair("School Life", "/tim-truyen/school-life"),
    Pair("Sci-fi", "/tim-truyen/sci-fi"),
    Pair("Shoujo", "/tim-truyen/shoujo"),
    Pair("Shoujo Ai", "/tim-truyen/shoujo-ai-126"),
    Pair("Shounen", "/tim-truyen/shounen-127"),
    Pair("Shounen Ai", "/tim-truyen/shounen-ai"),
    Pair("Slice of Life", "/tim-truyen/slice-of-life"),
    Pair("Sports", "/tim-truyen/sports"),
    Pair("Supernatural", "/tim-truyen/supernatural"),
    Pair("Thiếu Nhi", "/tim-truyen/thieu-nhi"),
    Pair("Tragedy", "/tim-truyen/tragedy-136"),
    Pair("Trinh Thám", "/tim-truyen/trinh-tham"),
    Pair("Truyện scan", "/tim-truyen/truyen-scan"),
    Pair("Truyện Màu", "/tim-truyen/truyen-mau"),
    Pair("Webtoon", "/tim-truyen/webtoon"),
    Pair("Xuyên Không", "/tim-truyen/xuyen-khong-205"),
    Pair("Tu Tiên", "/tim-truyen/tu-tien"),
)

val WebsiteFilterOptions: Array<Pair<String, String?>> = arrayOf(
    Pair("Tất cả", null),
    Pair("TruyenQQ", "/tag/truyenqq"),
    Pair("BlogTruyen", "/tag/blogtruyen"),
    Pair("TeamLanhLung", "/tag/teamlanhlung"),
    Pair("Tủ Sách Xinh Xinh", "/tag/tusachxinhxinh"),
    Pair("TruyenGiHot", "/tag/truyengihot"),
    Pair("Tu Tiên Truyện", "/tag/tutientruyen"),
    Pair("UngtyComics", "/tag/ungtycomics"),
    Pair("VyComycs - Tủ Sách Nhỏ", "/tag/vycomycs"),
    Pair("Bảo Tàng Truyện", "/tag/baotangtruyen"),
    Pair("Dưa Leo Truyện", "/tag/dualeotruyen"),
    Pair("Fastscan", "/tag/fastscan"),
    Pair("CManga", "/tag/cmanga"),
    Pair("FuHu", "/tag/fuhu"),
    Pair("Soái Ca Comic", "/tag/soaicacomic"),
    Pair("VlogTruyen", "/tag/vlogtruyen"),
    Pair("TopTruyện", "/tag/toptruyen"),
)

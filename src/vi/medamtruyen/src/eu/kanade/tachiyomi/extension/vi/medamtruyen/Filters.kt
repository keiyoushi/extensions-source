package eu.kanade.tachiyomi.extension.vi.medamtruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("Bộ lọc sẽ bị bỏ qua khi tìm kiếm"),
    GenreFilter(),
)

class GenreFilter :
    UriPartFilter(
        "Thể loại",
        arrayOf(
            Pair("Tất cả", null),
            Pair("Âu Cổ", "au-co"),
            Pair("Báo Thù", "bao-thu"),
            Pair("Chiếm Hữu Mạnh Mẽ", "chiem-huu-manh-me"),
            Pair("Cổ Đại", "co-dai"),
            Pair("Cổ Phong", "co-phong"),
            Pair("Cục Cưng", "cuc-cung"),
            Pair("Cưới Trước Yêu Sau", "cuoi-truoc-yeu-sau"),
            Pair("Harem", "harem"),
            Pair("Hậu Cung", "hau-cung"),
            Pair("Hệ Thống", "he-thong"),
            Pair("Hiện Đại", "hien-dai"),
            Pair("Hoàng Gia", "hoang-gia"),
            Pair("Hoạt Hình", "hoat-hinh"),
            Pair("Huyền Huyễn", "huyen-huyen"),
            Pair("Ma Cà Rồng", "ma-ca-rong"),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Ngôn Tình Hắc Đạo", "ngon-tinh-hac-dao"),
            Pair("Ngược Tâm", "nguoc-tam"),
            Pair("Nhân Thú", "nhan-thu"),
            Pair("Nữ Cường", "nu-cuong"),
            Pair("Nuôi Rồi Thịt", "nuoi-roi-thit"),
            Pair("Sét ⚡", "set-%e2%9a%a1"),
            Pair("Showbiz", "showbiz"),
            Pair("Sủng Ngọt", "sung-ngot"),
            Pair("Thanh Mai Trúc Mã", "thanh-mai-truc-ma"),
            Pair("Thanh Xuân Vườn Trường", "thanh-xuan-vuon-truong"),
            Pair("Tình Cảm Thầy Trò", "tinh-cam-thay-tro"),
            Pair("Tình Yêu Chị Em", "tinh-yeu-chi-em"),
            Pair("Tổng Tài", "tong-tai"),
            Pair("Trâu Già Gặm Cỏ Non", "trau-gia-gam-co-non"),
            Pair("Trùng Sinh", "trung-sinh"),
            Pair("Truyện Nữ Giả Nam", "truyen-nu-gia-nam"),
            Pair("Xuyên Không", "xuyen-khong"),
        ),
    )

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String?>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart(): String? = vals[state].second
}

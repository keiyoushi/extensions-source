package eu.kanade.tachiyomi.extension.vi.panomic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("Bộ lọc sẽ bị bỏ qua khi tìm kiếm"),
    GenreFilter(),
    GroupFilter(),
    SeriesTypeFilter(),
    KeywordFilter(),
)

class GenreFilter :
    UriPartFilter(
        "Thể Loại",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("18+", "the-loai/18"),
            Pair("Ảo Tưởng", "the-loai/ao-tuong"),
            Pair("Âu Cổ", "the-loai/au-co"),
            Pair("BL", "the-loai/bl"),
            Pair("Cổ Đại", "the-loai/co-dai"),
            Pair("Comedy", "the-loai/comedy"),
            Pair("Công Sở", "the-loai/cong-so"),
            Pair("Drama", "the-loai/drama"),
            Pair("GL", "the-loai/gl"),
            Pair("Hài Hước", "the-loai/hai-huoc"),
            Pair("Hành Động", "the-loai/hanh-dong"),
            Pair("Hệ Thống", "the-loai/he-thong"),
            Pair("Hiện Đại", "the-loai/hien-dai"),
            Pair("Hoàng Gia", "the-loai/hoang-gia"),
            Pair("Học Đường", "the-loai/hoc-duong"),
            Pair("Kinh Dị", "the-loai/kinh-di"),
            Pair("Manga", "the-loai/manga"),
            Pair("Manhua", "the-loai/manhua"),
            Pair("Manhwa", "the-loai/manhwa"),
            Pair("Mối Quan Hệ Tình Cảm Phức Tạp", "the-loai/moi-quan-he-tinh-cam-phuc-tap"),
            Pair("Nam Hối Hận", "the-loai/nam-hoi-han"),
            Pair("Ngôn Tình", "the-loai/ngon-tinh"),
            Pair("Ngược", "the-loai/nguoc"),
            Pair("Người Nổi Tiếng", "the-loai/nguoi-noi-tieng"),
            Pair("Người Sói", "the-loai/nguoi-soi"),
            Pair("Nữ Từng Bị Tổn Thương", "the-loai/nu-tung-bi-ton-thuong"),
            Pair("Oan Gia Ngõ Hẹp", "the-loai/oan-gia-ngo-hep"),
            Pair("Romance", "the-loai/romance"),
            Pair("Siêu Năng Lực", "the-loai/sieu-nang-luc"),
            Pair("Tài Phiệt", "the-loai/tai-phiet"),
            Pair("Thần Tượng", "the-loai/than-tuong"),
            Pair("Thanh Mai Trúc Mã", "the-loai/thanh-mai-truc-ma"),
            Pair("Thanh Xuân Vườn Trường", "the-loai/thanh-xuan-vuon-truong"),
            Pair("Tình Cảm", "the-loai/tinh-cam"),
            Pair("Tình Đầu", "the-loai/tinh-dau"),
            Pair("Trả Thù", "the-loai/tra-thu"),
            Pair("Trọng Sinh", "the-loai/trong-sinh"),
            Pair("Webtoon", "the-loai/webtoon"),
            Pair("Xuyên Không", "the-loai/xuyen-khong"),
            Pair("Yaoi", "the-loai/yaoi"),
            Pair("Yuri", "the-loai/yuri"),
        ),
    )

class GroupFilter :
    UriPartFilter(
        "Nhóm",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Bunnies Team", "nhom/bunnies-team"),
            Pair("Closed Heart", "nhom/closed-heart"),
            Pair("Công Túa Comics", "nhom/cong-tua-comics"),
            Pair("Eros Garden Comics", "nhom/eros-garden-comics"),
            Pair("Kho Gió", "nhom/kho-gio"),
            Pair("LIỀU nhiều chút", "nhom/lieu-nhieu-chut"),
            Pair("Ổ Dịch Truyện Nhà Seal", "nhom/o-dich-truyen-nha-seal"),
            Pair("Panda Team", "nhom/panda-team"),
            Pair("Phong Nguyệt Các Comic", "nhom/phong-nguyet-cac-comic"),
            Pair("Py Tarot", "nhom/py-tarot"),
            Pair("Solo", "nhom/solo"),
            Pair("Team Vilnius", "nhom/team-vilnius"),
            Pair("The Wolf Team", "nhom/the-wolf-team"),
            Pair("Thư Viện Nhà Cáo", "nhom/thu-vien-nha-cao"),
            Pair("Tĩnh Dạ", "nhom/tinh-da"),
        ),
    )

class SeriesTypeFilter :
    UriPartFilter(
        "Loại Truyện",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Hot Nhất", "truyen-hot-nhat"),
            Pair("Xem Nhiều", "nhieu-xem-nhat"),
            Pair("Trọn Bộ", "tron-bo"),
        ),
    )

class KeywordFilter :
    UriPartFilter(
        "Từ Khóa",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("#Hẹn Hò", "hen-ho"),
            Pair("#Nhân Vật", "nhan-vat"),
            Pair("#Phản Diện", "phan-dien"),
        ),
    )

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = vals[state].second
}

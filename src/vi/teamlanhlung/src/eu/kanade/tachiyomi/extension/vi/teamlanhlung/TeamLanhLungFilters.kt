package eu.kanade.tachiyomi.extension.vi.teamlanhlung

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
            Pair("Tất cả", ""),
            Pair("18+", "the-loai/18"),
            Pair("ABO", "the-loai/abo"),
            Pair("Ba ba", "the-loai/ba-ba"),
            Pair("Bách hợp", "the-loai/bach-hop"),
            Pair("boy love", "the-loai/boy-love"),
            Pair("chiếm hữu", "the-loai/chiem-huu"),
            Pair("chuyển thể", "the-loai/chuyen-the"),
            Pair("Cổ Đại", "the-loai/co-dai"),
            Pair("cổ trang", "the-loai/co-trang"),
            Pair("con gái nô", "the-loai/con-gai-no"),
            Pair("con trai mang thai", "the-loai/con-trai-mang-thai"),
            Pair("đam mỹ", "the-loai/dam-my"),
            Pair("độc chiếm", "the-loai/doc-chiem"),
            Pair("H+", "the-loai/h"),
            Pair("hài hước", "the-loai/hai-huoc"),
            Pair("hành động", "the-loai/hanh-dong"),
            Pair("harem", "the-loai/harem"),
            Pair("hệ thống", "the-loai/he-thong"),
            Pair("hiện đại", "the-loai/hien-dai"),
            Pair("học đường", "the-loai/hoc-duong"),
            Pair("Hư Cấu", "the-loai/hu-cau"),
            Pair("huyền ảo", "the-loai/huyen-ao"),
            Pair("kinh dị", "the-loai/kinh-di"),
            Pair("mạnh mẽ", "the-loai/manh-me"),
            Pair("manhua", "the-loai/manhua"),
            Pair("Manhwa", "the-loai/manhwa"),
            Pair("Ngôn Tình", "the-loai/ngon-tinh"),
            Pair("ngọt sủng", "the-loai/ngot-sung"),
            Pair("ngực nữ bự", "the-loai/nguc-nu-bu"),
            Pair("ngược", "the-loai/nguoc"),
            Pair("nữ cường", "the-loai/nu-cuong"),
            Pair("Nữ phụ ác độc", "the-loai/nu-phu-ac-doc"),
            Pair("phản diện", "the-loai/phan-dien"),
            Pair("séttttt", "the-loai/settttt"),
            Pair("Showbiz", "the-loai/showbiz"),
            Pair("smut", "the-loai/smut"),
            Pair("sư tôn", "the-loai/su-ton"),
            Pair("sủng", "the-loai/sung"),
            Pair("Thế thân", "the-loai/the-than"),
            Pair("thú thê", "the-loai/thu-the"),
            Pair("thức tỉnh", "the-loai/thuc-tinh"),
            Pair("Tiểu thuyết", "the-loai/tieu-thuyet"),
            Pair("Tình Yêu", "the-loai/tinh-yeu"),
            Pair("tổng tài", "the-loai/tong-tai"),
            Pair("trả thù", "the-loai/tra-thu"),
            Pair("trọng sinh", "the-loai/trong-sinh"),
            Pair("truyện con trai", "the-loai/truyen-con-trai"),
            Pair("tu tiên", "the-loai/tu-tien"),
            Pair("vườn trường", "the-loai/vuon-truong"),
            Pair("xuyên không", "the-loai/xuyen-khong"),
            Pair("xuyên nhan", "the-loai/xuyen-nhan"),
            Pair("xuyên nhanh", "the-loai/xuyen-nhanh"),
            Pair("xuyên sách", "the-loai/xuyen-sach"),
        ),
    )

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = vals[state].second
}

package eu.kanade.tachiyomi.extension.vi.vcomycs

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
            Pair("Trọn Bộ", "tron-bo"),
            Pair("Truyện Ngắn", "the-loai/truyen-ngan"),
            Pair("Không 18+", "khong-18"),
            Pair("18+", "the-loai/18"),
            Pair("19+", "the-loai/19"),
            Pair("Adult", "the-loai/adult"),
            Pair("Âu Cổ", "the-loai/au-co"),
            Pair("Bạn Tình", "the-loai/ban-tinh"),
            Pair("Bi Kịch", "the-loai/bi-kich"),
            Pair("Boy Love", "boy-love"),
            Pair("Chàng Trai Nhỏ Tuổi", "the-loai/chang-trai-nho-tuoi"),
            Pair("Drama", "the-loai/drama"),
            Pair("Fantasy", "the-loai/fantasy"),
            Pair("Hài Lãng Mạn", "the-loai/hai-lang-man"),
            Pair("Harem", "the-loai/harem"),
            Pair("Hệ Thống", "the-loai/he-thong"),
            Pair("Hiện Đại", "the-loai/hien-dai"),
            Pair("Hiểu Lầm", "the-loai/hieu-lam"),
            Pair("Horror", "the-loai/horror"),
            Pair("Josei", "the-loai/josei"),
            Pair("Kết Hôn Trước Yêu Sau", "the-loai/ket-hon-truoc-yeu-sau"),
            Pair("Lãng Mạn", "the-loai/lang-man"),
            Pair("Lãng tử quay đầu", "the-loai/lang-tu-quay-dau"),
            Pair("Manhwa", "the-loai/manhwa"),
            Pair("Mature", "the-loai/mature"),
            Pair("Mối Quan Hệ Bí Mật", "the-loai/moi-quan-he-bi-mat"),
            Pair("Mystery", "the-loai/mystery"),
            Pair("Ngôn Tình", "the-loai/ngon-tinh"),
            Pair("Ngược", "the-loai/nguoc"),
            Pair("Novel", "the-loai/novel"),
            Pair("Psychological", "the-loai/psychological"),
            Pair("Romance", "the-loai/romance"),
            Pair("Tragedy", "the-loai/tragedy"),
        ),
    )

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

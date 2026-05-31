package eu.kanade.tachiyomi.extension.vi.muntruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    GenreFilter(GENRE_LIST),
    StatusFilter(),
    AgeRatingFilter(),
    AuthorFilter(),
    TeamFilter(),
    SortFilter(),
)

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres) {
    fun selectedValues(): List<String> = state.filter { it.state }.map { it.value }
}

class StatusFilter : UriPartFilter("Trạng thái", STATUS_OPTIONS)

class AgeRatingFilter : UriPartFilter("Độ tuổi", AGE_RATING_OPTIONS)

class AuthorFilter : UriPartFilter("Tác giả", AUTHOR_OPTIONS)

class TeamFilter : UriPartFilter("Nhóm", TEAM_OPTIONS)

class SortFilter : UriPartFilter("Sắp xếp theo", SORT_OPTIONS)

open class UriPartFilter(displayName: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = options[state].second
}

private val GENRE_LIST = listOf(
    Genre("ABO", "abo"),
    Genre("Ba ba", "ba-ba"),
    Genre("Bách hợp", "bach-hop"),
    Genre("boy love", "boy-love"),
    Genre("chiếm hữu", "chiem-huu"),
    Genre("chuyển thể", "chuyen-the"),
    Genre("Cổ Đại", "co-dai"),
    Genre("cổ trang", "co-trang"),
    Genre("con gái nô", "con-gai-no"),
    Genre("con trai mang thai", "con-trai-mang-thai"),
    Genre("đam mỹ", "dam-my"),
    Genre("độc chiếm", "doc-chiem"),
    Genre("H+", "h"),
    Genre("hài hước", "hai-huoc"),
    Genre("hành động", "hanh-dong"),
    Genre("harem", "harem"),
    Genre("hệ thống", "he-thong"),
    Genre("hiện đại", "hien-dai"),
    Genre("học đường", "hoc-duong"),
    Genre("Hư Cấu", "hu-cau"),
    Genre("huyền ảo", "huyen-ao"),
    Genre("kinh dị", "kinh-di"),
    Genre("mạnh mẽ", "manh-me"),
    Genre("manhua", "manhua"),
    Genre("Manhwa", "manhwa"),
    Genre("Ngôn Tình", "ngon-tinh"),
    Genre("ngọt sủng", "ngot-sung"),
    Genre("ngực nữ bự", "nguc-nu-bu"),
    Genre("ngược", "nguoc"),
    Genre("nữ cường", "nu-cuong"),
    Genre("Nữ phụ ác độc", "nu-phu-ac-doc"),
    Genre("phản diện", "phan-dien"),
    Genre("Showbiz", "showbiz"),
    Genre("sư tôn", "su-ton"),
    Genre("sủng", "sung"),
    Genre("Thế thân", "the-than"),
    Genre("thú thê", "thu-the"),
    Genre("thức tỉnh", "thuc-tinh"),
    Genre("Tiểu thuyết", "tieu-thuyet"),
    Genre("Tình Yêu", "tinh-yeu"),
    Genre("tổng tài", "tong-tai"),
    Genre("trả thù", "tra-thu"),
    Genre("trọng sinh", "trong-sinh"),
    Genre("Truyện chữ ngắn", "truyen-chu-ngan"),
    Genre("truyện con trai", "truyen-con-trai"),
    Genre("tu tiên", "tu-tien"),
    Genre("vườn trường", "vuon-truong"),
    Genre("xuyên không", "xuyen-khong"),
    Genre("xuyên nhan", "xuyen-nhan"),
    Genre("xuyên nhanh", "xuyen-nhanh"),
    Genre("xuyên sách", "xuyen-sach"),
)

private val STATUS_OPTIONS = arrayOf(
    "Tất cả tình trạng" to "",
    "Đang tiến hành" to "ongoing",
    "Kết thúc mùa" to "season_end",
    "Trọn bộ" to "completed",
    "Nguồn tạm ngưng" to "source_hiatus",
    "Đã theo kịp" to "caught_up",
    "Bị hủy" to "dropped",
)

private val AGE_RATING_OPTIONS = arrayOf(
    "Tất cả" to "",
    "Mọi lứa tuổi" to "all",
    "13+" to "13+",
    "16+" to "16+",
    "18+" to "18+",
)

private val AUTHOR_OPTIONS = arrayOf(
    "Tất cả tác giả" to "",
    "Artist: Lộc Ma" to "54",
    "Kim Phỉ" to "88",
    "Koowa内容团队" to "84",
    "Nguyên tác: Mạn Tây" to "85",
    "thủ phủ kiều nương" to "86",
    "五彩石漫画社" to "57",
    "午夜眠睡" to "39",
    "安向暖" to "78",
    "庄宁" to "82",
    "晔越文化" to "81",
    "暴青漫画" to "74",
    "漫西（原著)" to "76",
    "班克西" to "77",
    "琥珀鼠" to "52",
    "白毛浮绿水" to "80",
    "绯恬" to "56",
    "阿柯文化" to "65",
    "야마겟돈" to "69",
    "연슬아" to "70",
)

private val TEAM_OPTIONS = arrayOf(
    "Tất cả nhóm" to "",
    "Anna Rio" to "72",
    "bắp" to "50",
    "BÍ NGÔ COMIC" to "89",
    "BỘ XƯƠNG KHÔ" to "51",
    "Cẩm Thường Hi" to "58",
    "Luna's Team" to "71",
    "Mèo Ú" to "48",
    "Snow panda" to "53",
    "Team Lạnh Lùng" to "46",
    "Xiao Zhu" to "68",
)

private val SORT_OPTIONS = arrayOf(
    "Mới cập nhật" to "updated",
    "Mới nhất" to "new",
    "Cũ nhất" to "old",
    "Nhiều lượt xem nhất" to "views",
    "Lượt xem hôm nay" to "views_day",
    "Lượt xem tuần này" to "views_week",
    "Lượt xem tháng này" to "views_month",
    "Đánh giá cao nhất" to "rating",
    "Sức mạnh nhiều nhất" to "power",
    "Nhiều người theo dõi nhất" to "follow",
)

package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import eu.kanade.tachiyomi.source.model.Filter

class Option(name: String, val id: String) : Filter.CheckBox(name)
open class FilterGroup(name: String, val query: String, state: List<Option>) : Filter.Group<Option>(name, state)

class StatusList(status: List<Option>) : FilterGroup("Trạng Thái", "status[]", status)

fun getStatusList() = listOf(
    Option("Đang thực hiện", "PRG"),
    Option("Hoàn thành", "END"),
    Option("Truyện Chữ", "novel"),
)

class SortByList(sort: List<Option>) : FilterGroup("Sắp xếp", "orders[]", sort)

fun getSortByList() = listOf(
    Option("Lượt xem", "viewCount"),
    Option("Lượt đánh giá", "evaluationScore"),
    Option("Lượt theo dõi", "followerCount"),
    Option("Ngày Cập Nhật", "recentDate"),
    Option("Truyện Mới", "createdAt"),
)

class GenreList(genres: List<Option>) : FilterGroup("Thể loại", "categories[]", genres)

fun getGenreList() = listOf(
    Option("Anime", "ANI"),
    Option("Drama", "DRA"),
    Option("Josei", "JOS"),
    Option("Manhwa", "MAW"),
    Option("One Shot", "OSH"),
    Option("Shounen", "SHO"),
    Option("Webtoons", "WEB"),
    Option("Shoujo", "SHJ"),
    Option("Harem", "HAR"),
    Option("Ecchi", "ECC"),
    Option("Mature", "MAT"),
    Option("Slice of life", "SOL"),
    Option("Isekai", "ISE"),
    Option("Manga", "MAG"),
    Option("Manhua", "MAU"),
    Option("Hành Động", "ACT"),
    Option("Phiêu Lưu", "ADV"),
    Option("Hài Hước", "COM"),
    Option("Võ Thuật", "MAA"),
    Option("Huyền Bí", "MYS"),
    Option("Lãng Mạn", "ROM"),
    Option("Thể Thao", "SPO"),
    Option("Học Đường", "SCL"),
    Option("Lịch Sử", "HIS"),
    Option("Kinh Dị", "HOR"),
    Option("Siêu Nhiên", "SUN"),
    Option("Bi Kịch", "TRA"),
    Option("Trùng Sinh", "RED"),
    Option("Game", "GAM"),
    Option("Viễn Tưởng", "FTS"),
    Option("Khoa Học", "SCF"),
    Option("Truyện Màu", "COI"),
    Option("Người Lớn", "ADU"),
    Option("BoyLove", "BBL"),
    Option("Hầm Ngục", "DUN"),
    Option("Săn Bắn", "HUNT"),
    Option("Ngôn Từ Nhạy Cảm", "NTNC"),
    Option("Doujinshi", "DOU"),
    Option("Bạo Lực", "BLM"),
    Option("Ngôn Tình", "NTT"),
    Option("Nữ Cường", "NCT"),
    Option("Gender Bender", "GDB"),
    Option("Murim", "MRR"),
    Option("Leo Tháp", "LTT"),
    Option("Nấu Ăn", "COO"),
)

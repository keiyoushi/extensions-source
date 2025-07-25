import eu.kanade.tachiyomi.source.model.Filter

class StatusList(status: List<Status>) : Filter.Group<Status>("Trạng Thái", status)
class Status(name: String, val id: String) : Filter.CheckBox(name)
fun getStatusList() = listOf(
    Status("Chưa bắt đầu", "STA"),
    Status("Đã dừng", "STO"),
    Status("Hoãn lại", "PDG"),
    Status("Đang thực hiện", "PRG"),
    Status("Hoàn thành", "END"),
    Status("Truyện Chữ", "novel"),
)
class SortByList(sort: List<SortBy>) : Filter.Group<SortBy>("Sắp xếp", sort)
class SortBy(name: String, val id: String) : Filter.CheckBox(name)
fun getSortByList() = listOf(
    SortBy("Lượt xem", "viewCount"),
    SortBy("Lượt đánh giá", "evaluationScore"),
    SortBy("Lượt theo dõi", "followerCount"),
    SortBy("Ngày Cập Nhật", "recentDate"),
    SortBy("Truyện Mới", "createdAt"),
)
class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)
class Genre(name: String, val id: String) : Filter.CheckBox(name)

fun getGenreList() = listOf(
    Genre("Anime", "ANI"),
    Genre("Drama", "DRA"),
    Genre("Josei", "JOS"),
    Genre("Manhwa", "MAW"),
    Genre("One Shot", "OSH"),
    Genre("Shounen", "SHO"),
    Genre("Webtoons", "WEB"),
    Genre("Shoujo", "SHJ"),
    Genre("Harem", "HAR"),
    Genre("Ecchi", "ECC"),
    Genre("Mature", "MAT"),
    Genre("Slice of life", "SOL"),
    Genre("Isekai", "ISE"),
    Genre("Manga", "MAG"),
    Genre("Manhua", "MAU"),
    Genre("Hành Động", "ACT"),
    Genre("Phiêu Lưu", "ADV"),
    Genre("Hài Hước", "COM"),
    Genre("Võ Thuật", "MAA"),
    Genre("Huyền Bí", "MYS"),
    Genre("Lãng Mạn", "ROM"),
    Genre("Thể Thao", "SPO"),
    Genre("Học Đường", "SCL"),
    Genre("Lịch Sử", "HIS"),
    Genre("Kinh Dị", "HOR"),
    Genre("Siêu Nhiên", "SUN"),
    Genre("Bi Kịch", "TRA"),
    Genre("Trùng Sinh", "RED"),
    Genre("Game", "GAM"),
    Genre("Viễn Tưởng", "FTS"),
    Genre("Khoa Học", "SCF"),
    Genre("Truyện Màu", "COI"),
    Genre("Người Lớn", "ADU"),
    Genre("BoyLove", "BBL"),
    Genre("Hầm Ngục", "DUN"),
    Genre("Săn Bắn", "HUNT"),
    Genre("Ngôn Từ Nhạy Cảm", "NTNC"),
    Genre("Doujinshi", "DOU"),
    Genre("Bạo Lực", "BLM"),
    Genre("Ngôn Tình", "NTT"),
    Genre("Nữ Cường", "NCT"),
    Genre("Gender Bender", "GDB"),
    Genre("Murim", "MRR"),
    Genre("Leo Tháp", "LTT"),
    Genre("Nấu Ăn", "COO"),
)

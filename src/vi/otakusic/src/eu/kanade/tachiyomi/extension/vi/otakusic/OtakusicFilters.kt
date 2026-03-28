package eu.kanade.tachiyomi.extension.vi.otakusic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    Filter.Header("Bộ lọc không áp dụng khi tìm kiếm bằng từ khóa"),
    SortFilter(),
    StatusFilter(),
    GenreFilter(),
)

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp",
        arrayOf("Mới cập nhật", "Lượt xem", "Tên A-Z"),
    ) {
    fun toUriPart() = when (state) {
        1 -> "views"
        2 -> "name"
        else -> "updated"
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        arrayOf("Tất cả", "Đang tiến hành", "Đã hoàn thành"),
    ) {
    fun toUriPart(): String? = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        else -> null
    }
}

class GenreFilter :
    Filter.Select<String>(
        "Thể loại",
        arrayOf("Tất cả") + genres.map { it.first }.toTypedArray(),
    ) {
    fun toUriPart(): String? = if (state == 0) null else genres[state - 1].second
}

private val genres = listOf(
    Pair("15+", "15"),
    Pair("16+", "16"),
    Pair("18+", "18"),
    Pair("ABO", "abo"),
    Pair("Action", "action"),
    Pair("Adaptation", "adaptation"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("Animal", "animal"),
    Pair("Anime", "anime"),
    Pair("Âu cổ", "au-co"),
    Pair("Baby", "baby"),
    Pair("Bách Hợp", "bach-hop"),
    Pair("BE", "be"),
    Pair("Betrayal", "betrayal"),
    Pair("Bí ẩn", "bi-an"),
    Pair("Bi Kịch", "bi-kich"),
    Pair("BL", "bl"),
    Pair("BLHĐ", "blhd"),
    Pair("Boy Tâm Cơ", "boy-tam-co"),
    Pair("Childcare", "childcare"),
    Pair("Chủ Thụ", "chu-thu"),
    Pair("Chữa Lành", "chua-lanh"),
    Pair("Chuyển Sinh", "chuyen-sinh"),
    Pair("Cổ Trang", "co-trang"),
    Pair("Cổ Đại", "co-dai"),
    Pair("Comedy", "comedy"),
    Pair("Comic", "comic"),
    Pair("Cooking", "cooking"),
    Pair("Cưới trước yêu sau", "cuoi-truoc-yeu-sau"),
    Pair("Cứu Drop", "cuu-drop"),
    Pair("Dark", "dark"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Drama", "drama"),
    Pair("Ecchi", "ecchi"),
    Pair("Esper and Guide", "esper-and-guide"),
    Pair("Fantasy", "fantasy"),
    Pair("First Love", "first-love"),
    Pair("Full Color", "full-color"),
    Pair("Game", "game"),
    Pair("Gender Bender", "gender-bender"),
    Pair("Gender Blender", "gender-blender"),
    Pair("Ghosts", "ghosts"),
    Pair("Giật gân", "giat-gan"),
    Pair("Giới Giải Trí", "gioi-giai-tri"),
    Pair("GL", "gl"),
    Pair("Hài Hước", "hai-huoc"),
    Pair("Hầm Ngục", "ham-nguc"),
    Pair("Harem", "harem"),
    Pair("Hệ Thống", "he-thong"),
    Pair("Hiện Đại", "hien-dai"),
    Pair("Historical", "historical"),
    Pair("Hoán đổi thân xác", "hoan-doi-than-xac"),
    Pair("Hoàng Gia", "hoang-gia"),
    Pair("Học Đường", "hoc-duong"),
    Pair("Horror", "horror"),
    Pair("Huyền Huyễn", "huyen-huyen"),
    Pair("Isekai", "isekai"),
    Pair("Josei", "josei"),
    Pair("Josei(W)", "josei-w"),
    Pair("Kingdom", "kingdom"),
    Pair("Kinh Dị", "kinh-di"),
    Pair("Lãng Mạn", "lang-man"),
    Pair("Live action", "live-action"),
    Pair("Love Triangle", "love-triangle"),
    Pair("Ma Cà Rồng", "ma-ca-rong"),
    Pair("Magic", "magic"),
    Pair("Mahou", "mahou"),
    Pair("Manga", "manga"),
    Pair("Manhua", "manhua"),
    Pair("Manhwa", "manhwa"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Mất Trí Nhớ", "mat-tri-nho"),
    Pair("Mature", "mature"),
    Pair("Mecha", "mecha"),
    Pair("Mystery", "mystery"),
    Pair("Ngôn Tình", "ngon-tinh"),
    Pair("Ngọt Sủng", "ngot-sung"),
    Pair("Ngược", "nguoc"),
    Pair("Nhân Thú", "nhan-thu"),
    Pair("Novel", "novel"),
    Pair("Nữ chủ", "nu-chu"),
    Pair("Nữ Cường", "nu-cuong"),
    Pair("One shot", "one-shot"),
    Pair("Oneshot", "oneshot"),
    Pair("Phép Thuật", "phep-thuat"),
    Pair("Psychological", "psychological"),
    Pair("Reincarnation", "reincarnation"),
    Pair("Revenge", "revenge"),
    Pair("Reverse Harem", "reverse-harem"),
    Pair("Romance", "romance"),
    Pair("Romance Comedy", "romance-comedy"),
    Pair("School Life", "school-life"),
    Pair("Sci-fi", "sci-fi"),
    Pair("Second Chance", "second-chance"),
    Pair("Seinen", "seinen"),
    Pair("Shoujo", "shoujo"),
    Pair("Shoujo Ai", "shoujo-ai"),
    Pair("Shounen", "shounen"),
    Pair("Shounen Ai", "shounen-ai"),
    Pair("Showbiz", "showbiz"),
    Pair("Siêu Năng Lực", "sieu-nang-luc"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Smut", "smut"),
    Pair("Soft Yaoi", "soft-yaoi"),
    Pair("Soft Yuri", "soft-yuri"),
    Pair("Sports", "sports"),
    Pair("Supernatural", "supernatural"),
    Pair("Tâm lí", "tam-li"),
    Pair("Tạp chí truyện tranh", "tap-chi-truyen-tranh"),
    Pair("Thần tượng", "than-tuong"),
    Pair("Thanh mai trúc mã", "thanh-mai-truc-ma"),
    Pair("Thanh Xuân Vườn Trường", "thanh-xuan-vuon-truong"),
    Pair("Thiếu Nhi", "thieu-nhi"),
    Pair("Thriller", "thriller"),
    Pair("Tiểu Thuyết", "tieu-thuyet"),
    Pair("Time Travel", "time-travel"),
    Pair("Tình Cảm", "tinh-cam"),
    Pair("Tình Yêu Công Sở", "tinh-yeu-cong-so"),
    Pair("Tình Yêu Kém Tuổi", "tinh-yeu-kem-tuoi"),
    Pair("Tổng Tài", "tong-tai"),
    Pair("Trả Thù", "tra-thu"),
    Pair("Tragedy", "tragedy"),
    Pair("Trinh Thám", "trinh-tham"),
    Pair("Trọng Sinh", "trong-sinh"),
    Pair("Trùng Sinh", "trung-sinh"),
    Pair("Truyện Màu", "truyen-mau"),
    Pair("Truyện scan", "truyen-scan"),
    Pair("Tu Tiên", "tu-tien"),
    Pair("Việt Nam", "viet-nam"),
    Pair("Webtoon", "webtoon"),
    Pair("Xuyên Không", "xuyen-khong"),
    Pair("Xuyên Sách", "xuyen-sach"),
    Pair("Yandere", "yandere"),
    Pair("Yuri", "yuri"),
    Pair("Đam Mỹ", "dam-my"),
    Pair("Đô Thị", "do-thi"),
    Pair("Đơn Phương", "don-phuong"),
)

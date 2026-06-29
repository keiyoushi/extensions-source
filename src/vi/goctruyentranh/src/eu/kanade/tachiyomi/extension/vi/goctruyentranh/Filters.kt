package eu.kanade.tachiyomi.extension.vi.goctruyentranh

import eu.kanade.tachiyomi.source.model.Filter

class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

class Genre(name: String, val id: String) : Filter.CheckBox(name) {
    override fun toString() = name
}

class CountryList :
    Filter.Group<Genre>(
        "Quốc gia",
        listOf(
            Genre("Nhật Bản", "manga"),
            Genre("Trung Quốc", "manhua"),
            Genre("Hàn Quốc", "manhwa"),
            Genre("Khác", "other"),
        ),
    )
class SortByList :
    Filter.Select<Genre>(
        "Sắp xếp",
        arrayOf(
            Genre("Không", ""),
            Genre("Mới nhất", "latest"),
            Genre("Cũ nhất", "oldest"),
            Genre("Đánh giá", "rating"),
            Genre("A-Z", "alphabet"),
            Genre("Mới cập nhật", "recently_updated"),
            Genre("Xem nhiều nhất", "mostView"),
        ),
    )
class ChapterCountList :
    Filter.Select<Genre>(
        "Độ dài",
        arrayOf(
            Genre("Không", ""),
            Genre(">= 1 chapters", "1"),
            Genre(">= 3 chapters", "3"),
            Genre(">= 5 chapters", "5"),
            Genre(">= 10 chapters", "10"),
            Genre(">= 20 chapters", "20"),
            Genre(">= 30 chapters", "30"),
            Genre(">= 50 chapters", "50"),
        ),
    )
class StatusList :
    Filter.Group<Genre>(
        "Trạng Thái",
        listOf(
            Genre("Hoàn thành", "1"),
            Genre("Đang tiến hành", "0"),
        ),
    )
fun getGenreList() = listOf(
    Genre("Action", "1"),
    Genre("Adventure", "2"),
    Genre("Fantasy", "3"),
    Genre("Manhua", "4"),
    Genre("Chuyển Sinh", "5"),
    Genre("Truyện Màu", "6"),
    Genre("Xuyên Không", "7"),
    Genre("Manhwa", "8"),
    Genre("Drama", "9"),
    Genre("Historical", "10"),
    Genre("Manga", "11"),
    Genre("Seinen", "12"),
    Genre("Comedy", "13"),
    Genre("Martial Arts", "14"),
    Genre("Mystery", "15"),
    Genre("Romance", "16"),
    Genre("Shounen", "17"),
    Genre("Sports", "18"),
    Genre("Supernatural", "19"),
    Genre("Harem", "20"),
    Genre("Webtoon", "21"),
    Genre("School", "22"),
    Genre("Psychological", "23"),
    Genre("Cổ Đại", "24"),
    Genre("Ecchi", "25"),
    Genre("Gender Bender", "26"),
    Genre("Shoujo", "27"),
    Genre("Slice of Life", "28"),
    Genre("Ngôn Tình", "29"),
    Genre("Horror", "30"),
    Genre("Sci-fi", "31"),
    Genre("Tragedy", "32"),
    Genre("Mecha", "33"),
    Genre("Comic", "34"),
    Genre("One shot", "35"),
    Genre("Shoujo Ai", "36"),
    Genre("Anime", "37"),
    Genre("Josei", "38"),
    Genre("Smut", "39"),
    Genre("Shounen Ai", "40"),
    Genre("Mature", "41"),
    Genre("Soft Yuri", "42"),
    Genre("Adult", "43"),
    Genre("Doujinshi", "44"),
    Genre("Live action", "45"),
    Genre("Trinh Thám", "46"),
    Genre("Việt Nam", "47"),
    Genre("Truyện Scan", "48"),
    Genre("Cooking", "49"),
    Genre("Tạp chí truyện tranh", "50"),
    Genre("16+", "51"),
    Genre("Thiếu Nhi", "52"),
    Genre("Soft Yaoi", "53"),
    Genre("Đam Mỹ", "54"),
    Genre("BoyLove", "55"),
    Genre("Yaoi", "56"),
    Genre("18+", "57"),
    Genre("Người Thú", "58"),
    Genre("ABO", "59"),
    Genre("Mafia", "60"),
    Genre("Isekai", "61"),
    Genre("Hệ Thống", "62"),
    Genre("NTR", "63"),
    Genre("Yuri", "64"),
    Genre("Girl Love", "65"),
    Genre("Demons", "66"),
    Genre("Huyền Huyễn", "67"),
    Genre("Detective", "68"),
    Genre("Trọng Sinh", "69"),
    Genre("Magic", "70"),
    Genre("Military", "71"),
)

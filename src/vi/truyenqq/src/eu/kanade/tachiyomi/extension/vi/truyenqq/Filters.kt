package eu.kanade.tachiyomi.extension.vi.truyenqq

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val id: String) : Filter.TriState(name) {
    override fun toString(): String = name
}

class CountryFilter :
    Filter.Select<Genre>(
        "Quốc gia",
        arrayOf(
            Genre("Tất cả", "0"),
            Genre("Trung Quốc", "1"),
            Genre("Việt Nam", "2"),
            Genre("Hàn Quốc", "3"),
            Genre("Nhật Bản", "4"),
            Genre("Mỹ", "5"),
        ),
    )

class StatusFilter :
    Filter.Select<Genre>(
        "Tình trạng",
        arrayOf(
            Genre("Tất cả", "-1"),
            Genre("Đang tiến hành", "0"),
            Genre("Hoàn thành", "2"),
        ),
    )

class ChapterCountFilter :
    Filter.Select<Genre>(
        "Số lượng chương",
        arrayOf(
            Genre("0", "0"),
            Genre(">= 100", "100"),
            Genre(">= 200", "200"),
            Genre(">= 300", "300"),
            Genre(">= 400", "400"),
            Genre(">= 500", "500"),
        ),
    )

class SortByFilter :
    Filter.Sort(
        "Sắp xếp",
        arrayOf("Ngày đăng", "Ngày cập nhật", "Lượt xem"),
        Selection(2, ascending = false),
    )

class GenreList(state: List<Genre>) : Filter.Group<Genre>("Thể loại", state)

fun getGenreList() = listOf(
    Genre("Action", "26"),
    Genre("Adventure", "27"),
    Genre("Anime", "62"),
    Genre("Chuyển Sinh", "91"),
    Genre("Cổ Đại", "90"),
    Genre("Comedy", "28"),
    Genre("Comic", "60"),
    Genre("Demons", "99"),
    Genre("Detective", "100"),
    Genre("Doujinshi", "96"),
    Genre("Drama", "29"),
    Genre("Fantasy", "30"),
    Genre("Gender Bender", "45"),
    Genre("Harem", "47"),
    Genre("Historical", "51"),
    Genre("Horror", "44"),
    Genre("Huyền Huyễn", "468"),
    Genre("Isekai", "85"),
    Genre("Josei", "54"),
    Genre("Mafia", "69"),
    Genre("Magic", "58"),
    Genre("Manhua", "35"),
    Genre("Manhwa", "49"),
    Genre("Martial Arts", "41"),
    Genre("Military", "101"),
    Genre("Mystery", "39"),
    Genre("Ngôn Tình", "87"),
    Genre("One shot", "95"),
    Genre("Psychological", "40"),
    Genre("Romance", "36"),
    Genre("School Life", "37"),
    Genre("Sci-fi", "43"),
    Genre("Seinen", "42"),
    Genre("Shoujo", "38"),
    Genre("Shoujo Ai", "98"),
    Genre("Shounen", "31"),
    Genre("Shounen Ai", "86"),
    Genre("Slice of life", "46"),
    Genre("Sports", "57"),
    Genre("Supernatural", "32"),
    Genre("Tragedy", "52"),
    Genre("Trọng Sinh", "82"),
    Genre("Truyện Màu", "92"),
    Genre("Webtoon", "55"),
    Genre("Xuyên Không", "88"),
)

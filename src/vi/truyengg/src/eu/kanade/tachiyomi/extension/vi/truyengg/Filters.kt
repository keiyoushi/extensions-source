package eu.kanade.tachiyomi.extension.vi.truyengg

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val id: String) : Filter.TriState(name) {
    override fun toString(): String = name
}

class Option(name: String, val id: String)

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
        Selection(1, false),
    )

class GenreList(state: List<Genre>) : Filter.Group<Genre>("Thể loại", state)

fun getGenreList() = listOf(
    Genre("Action", "37"),
    Genre("Adventure", "38"),
    Genre("Anime", "39"),
    Genre("Cổ Đại", "40"),
    Genre("Comedy", "41"),
    Genre("Comic", "42"),
    Genre("Detective", "43"),
    Genre("Doujinshi", "44"),
    Genre("Drama", "45"),
    Genre("Ecchi", "80"),
    Genre("Fantasy", "46"),
    Genre("Gender Bender", "47"),
    Genre("Harem", "78"),
    Genre("Historical", "48"),
    Genre("Horror", "49"),
    Genre("Huyền Huyễn", "50"),
    Genre("Isekai", "51"),
    Genre("Josei", "52"),
    Genre("Magic", "53"),
    Genre("Manga", "81"),
    Genre("Manhua", "54"),
    Genre("Manhwa", "55"),
    Genre("Martial Arts", "56"),
    Genre("Mystery", "57"),
    Genre("Ngôn Tình", "58"),
    Genre("One shot", "59"),
    Genre("Psychological", "60"),
    Genre("Romance", "61"),
    Genre("School Life", "62"),
    Genre("Sci-fi", "63"),
    Genre("Seinen", "64"),
    Genre("Shoujo", "65"),
    Genre("Shoujo Ai", "66"),
    Genre("Shounen", "67"),
    Genre("Shounen Ai", "68"),
    Genre("Slice of life", "69"),
    Genre("Sports", "70"),
    Genre("Supernatural", "71"),
    Genre("Tragedy", "72"),
    Genre("Truyện Màu", "73"),
    Genre("Webtoon", "74"),
    Genre("Xuyên Không", "75"),
    Genre("Yuri", "76"),
)

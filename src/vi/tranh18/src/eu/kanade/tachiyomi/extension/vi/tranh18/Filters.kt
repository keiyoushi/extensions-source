package eu.kanade.tachiyomi.extension.vi.tranh18

import eu.kanade.tachiyomi.source.model.Filter

class Genre(val name: String, val genre: String) {
    override fun toString() = name
}

class GenreList :
    Filter.Select<Genre>(
        "Thể loại",
        arrayOf(
            Genre("Tất cả", "-1"),
            Genre("Manhua", "1"),
            Genre("Manhwa", "2"),
            Genre("Manga", "3"),
        ),
    )

class StatusList :
    Filter.Select<Genre>(
        "Tiến độ",
        arrayOf(
            Genre("Tất cả", "-1"),
            Genre("Đang tiến thành", "2"),
            Genre("Đã hoàn tất", "1"),
        ),
    )

class KeywordList(genre: Array<Genre>) : Filter.Select<Genre>("Từ khóa", genre)

fun getGenreList() = arrayOf(
    Genre("All", "All"),
    Genre("Adult", "Adult"),
    Genre("Action", "Action"),
    Genre("Comedy", "Comedy"),
    Genre("Drama", "Drama"),
    Genre("Fantasy", "Fantasy"),
    Genre("Harem", "Harem"),
    Genre("Historical", "Historical"),
    Genre("Horror", "Horror"),
    Genre("Ecchi", "Ecchi"),
    Genre("School Life", "School Life"),
    Genre("Seinen", "Seinen"),
    Genre("Shoujo", "Shoujo"),
    Genre("Shoujo Ai", "Shoujo Ai"),
    Genre("Shounen", "Shounen"),
    Genre("Shounen Ai", "Shounen Ai"),
    Genre("Mystery", "Mystery"),
    Genre("Sci-fi", "Sci-fi"),
    Genre("Webtoon", "Webtoon"),
    Genre("Chuyển Sinh", "Chuyển Sinh"),
    Genre("Xuyên Không", "Xuyên Không"),
    Genre("Truyện Màu", "Truyện Màu"),
    Genre("18", "18"),
    Genre("Truyện Tranh 18", "Truyện Tranh 18"),
    Genre("Big Boobs", "Big Boobs"),
)

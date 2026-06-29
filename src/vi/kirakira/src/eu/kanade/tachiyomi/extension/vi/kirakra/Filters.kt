package eu.kanade.tachiyomi.extension.vi.kirakira

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class GenreFilter :
    Filter.Select<String>(
        "Thể loại",
        GENRES.map { it.name }.toTypedArray(),
    ) {
    val selected: GenreOption
        get() = GENRES[state]
}

fun getFilters(): FilterList = FilterList(
    Filter.Header("Lọc theo thể loại"),
    GenreFilter(),
)

class GenreOption(
    val name: String,
    val id: String? = null,
)

private val GENRES = arrayOf(
    GenreOption("Tất cả"),
    GenreOption("Action", "47"),
    GenreOption("Adventure", "48"),
    GenreOption("Anime", "11"),
    GenreOption("Chuyển Sinh", "12"),
    GenreOption("Comedy", "49"),
    GenreOption("Comic", "14"),
    GenreOption("Cổ Đại", "13"),
    GenreOption("Demons", "15"),
    GenreOption("Detective", "16"),
    GenreOption("Doujinshi", "17"),
    GenreOption("Drama", "52"),
    GenreOption("Fantasy", "53"),
    GenreOption("Gender Bender", "18"),
    GenreOption("Harem", "19"),
    GenreOption("Historical", "20"),
    GenreOption("Horror", "55"),
    GenreOption("Huyền Huyễn", "21"),
    GenreOption("Isekai", "22"),
    GenreOption("Josei", "23"),
    GenreOption("Mafia", "24"),
    GenreOption("Magic", "25"),
    GenreOption("Manga", "26"),
    GenreOption("Manhua", "27"),
    GenreOption("Manhwa", "28"),
    GenreOption("Martial Arts", "29"),
    GenreOption("Military", "30"),
    GenreOption("Mystery", "31"),
    GenreOption("Ngôn Tình", "32"),
    GenreOption("One shot", "33"),
    GenreOption("Psychological", "34"),
    GenreOption("Romance", "56"),
    GenreOption("School Life", "35"),
    GenreOption("Sci-fi", "54"),
    GenreOption("Seinen", "36"),
    GenreOption("Shoujo", "37"),
    GenreOption("Shoujo Ai", "38"),
    GenreOption("Shounen", "39"),
    GenreOption("Shounen Ai", "40"),
    GenreOption("Slice of Life", "50"),
    GenreOption("Sports", "51"),
    GenreOption("Supernatural", "41"),
    GenreOption("Tragedy", "42"),
    GenreOption("Truyện Màu", "44"),
    GenreOption("Trọng Sinh", "43"),
    GenreOption("Webtoon", "45"),
    GenreOption("Xuyên Không", "46"),
)

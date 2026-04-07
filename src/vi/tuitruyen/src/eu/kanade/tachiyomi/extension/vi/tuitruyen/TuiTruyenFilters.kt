package eu.kanade.tachiyomi.extension.vi.tuitruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    StatusFilter(),
    GenreFilter(getGenreList()),
)

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        arrayOf("Tất cả", "Còn tiếp", "Hoàn thành"),
    ) {
    fun toUriPart(): String? = when (state) {
        1 -> "Còn tiếp"
        2 -> "Hoàn thành"
        else -> null
    }
}

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

private fun getGenreList(): List<Genre> = listOf(
    Genre("Action", "2"),
    Genre("Adventure", "20"),
    Genre("Chuyển Sinh", "29"),
    Genre("Comedy", "17"),
    Genre("Cooking", "39"),
    Genre("Crime", "12"),
    Genre("Cyberpunk", "46"),
    Genre("Demon", "6"),
    Genre("Detective", "24"),
    Genre("Doujinshi", "43"),
    Genre("Drama", "13"),
    Genre("Ecchi", "44"),
    Genre("Fantasy", "4"),
    Genre("Gangster", "50"),
    Genre("Gender Bender", "45"),
    Genre("Gourmet", "40"),
    Genre("Harem", "35"),
    Genre("Historical", "41"),
    Genre("Horror", "25"),
    Genre("Isekai", "11"),
    Genre("Josei", "19"),
    Genre("Kodomo", "34"),
    Genre("Mafia", "49"),
    Genre("Magic", "7"),
    Genre("Martial Arts", "42"),
    Genre("Mecha", "26"),
    Genre("Medical", "48"),
    Genre("Military", "32"),
    Genre("Monster", "5"),
    Genre("Mystery", "14"),
    Genre("Oneshot", "1"),
    Genre("Psychological", "16"),
    Genre("Reverse Harem", "36"),
    Genre("Romance", "21"),
    Genre("School Life", "10"),
    Genre("Sci-Fi", "22"),
    Genre("Seinen", "15"),
    Genre("Shoujo", "18"),
    Genre("Shoujo-Ai", "30"),
    Genre("Shounen", "3"),
    Genre("Shounen-Ai", "8"),
    Genre("Slice Of Life", "28"),
    Genre("Sports", "23"),
    Genre("Steampunk", "47"),
    Genre("Supernatural", "27"),
    Genre("Thriller", "38"),
    Genre("Tiên Hiệp", "33"),
    Genre("Tragedy", "37"),
    Genre("Yaoi", "9"),
)

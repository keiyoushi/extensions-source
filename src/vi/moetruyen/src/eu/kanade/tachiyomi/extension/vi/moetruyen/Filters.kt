package eu.kanade.tachiyomi.extension.vi.moetruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    StatusFilter(),
    GenreFilter(getGenreList()),
)

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        arrayOf("Tất cả", "Còn tiếp", "Hoàn thành", "Tạm dừng"),
    ) {
    fun toUriPart(): String? = when (state) {
        1 -> "Còn tiếp"
        2 -> "Hoàn thành"
        3 -> "Tạm dừng"
        else -> null
    }
}

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

private fun getGenreList(): List<Genre> = listOf(
    Genre("Action", "1"),
    Genre("Adult", "212"),
    Genre("Adventure", "12"),
    Genre("Comedy", "13"),
    Genre("Crossdressing", "68"),
    Genre("Drama", "4"),
    Genre("Ecchi", "64"),
    Genre("Fantasy", "15"),
    Genre("Gender Bender", "67"),
    Genre("Harem", "65"),
    Genre("Historical", "26"),
    Genre("Horror", "16"),
    Genre("Isekai", "46"),
    Genre("Josei", "44"),
    Genre("Martial Arts", "192"),
    Genre("Mature", "73"),
    Genre("Mystery", "2"),
    Genre("Oneshot", "76"),
    Genre("Psychological", "6"),
    Genre("Romance", "19"),
    Genre("School Life", "27"),
    Genre("Sci-Fi", "7"),
    Genre("Seinen", "3"),
    Genre("Shoujo", "2703"),
    Genre("Shounen", "8"),
    Genre("Slice Of Life", "21"),
    Genre("Supernatural", "22"),
    Genre("Time Travel", "49"),
    Genre("Tragedy", "39"),
    Genre("Yaoi", "2710"),
    Genre("Yuri", "70"),
)

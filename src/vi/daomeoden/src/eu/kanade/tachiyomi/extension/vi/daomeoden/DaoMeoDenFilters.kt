package eu.kanade.tachiyomi.extension.vi.daomeoden

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    StatusFilter(),
    CategoryFilter(),
    GenreFilter(),
    ExplicitFilter(),
    SortFilter(),
)

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", "0"),
            Pair("Full", "1"),
            Pair("On Going", "2"),
            Pair("Drop", "3"),
            Pair("Comming Soon...", "9"),
        ),
    )

class CategoryFilter :
    UriPartFilter(
        "Category",
        arrayOf(
            Pair("All", "all"),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Tự Sáng Tác", "tu-sang-tac"),
        ),
    )

class GenreFilter :
    UriPartFilter(
        "Genre",
        arrayOf(
            Pair("All", "0"),
            Pair("Action", "9"),
            Pair("Adventure", "11"),
            Pair("Boys' Love", "296"),
            Pair("Comedy", "294"),
            Pair("Crime", "299"),
            Pair("Drama", "19"),
            Pair("Fantasy", "22"),
            Pair("Girl's Love", "297"),
            Pair("Historical", "300"),
            Pair("Horror", "310"),
            Pair("Isekai", "304"),
            Pair("Magical", "311"),
            Pair("Martial Arts", "298"),
            Pair("Mecha", "308"),
            Pair("Mystery", "285"),
            Pair("Philosophical", "312"),
            Pair("Psychological", "286"),
            Pair("Romance", "295"),
            Pair("Sci-fi", "309"),
            Pair("Slice of life", "288"),
            Pair("Superhero", "305"),
            Pair("Thriller", "306"),
            Pair("Tragedy", "303"),
        ),
    )

class ExplicitFilter :
    UriPartFilter(
        "Explicit",
        arrayOf(
            Pair("All", "0"),
            Pair("Ecchi", "21"),
            Pair("Hentai", "73"),
            Pair("Oneshot", "230"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Order",
        arrayOf(
            Pair("Ngày cập nhật", "updated_at"),
            Pair("Ngày đăng", "created_at"),
            Pair("Lượt xem", "viewsAll"),
        ),
    )

open class UriPartFilter(
    displayName: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = options[state].second
}

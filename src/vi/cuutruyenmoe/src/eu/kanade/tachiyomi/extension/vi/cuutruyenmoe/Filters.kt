package eu.kanade.tachiyomi.extension.vi.cuutruyenmoe

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        add(SortFilter())
        add(StatusFilter())
        genres?.let { add(GenreFilter(it.map { genre -> Genre(genre.name, genre.id) })) }
    },
)

@Serializable
class GenreOption(
    val name: String,
    val id: String,
)

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            Pair("Mới cập nhật", "-updated_at"),
            Pair("Mới nhất", "-created_at"),
            Pair("Cũ nhất", "created_at"),
            Pair("Xem nhiều", "-views"),
            Pair("A-Z", "name"),
            Pair("Z-A", "-name"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            Pair("Tất cả", "2,1"),
            Pair("Đang tiến hành", "2"),
            Pair("Đã hoàn thành", "1"),
        ),
    )

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

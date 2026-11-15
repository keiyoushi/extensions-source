package eu.kanade.tachiyomi.extension.vi.vihentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        add(SortFilter())
        add(StatusFilter())
        genres?.takeIf { it.isNotEmpty() }?.let { options ->
            add(GenreFilter(options.map { Genre(it.name, it.id) }))
        }
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
            Pair("Xem nhiều", "-views"),
            Pair("Mới cập nhật", "-updated_at"),
            Pair("Mới nhất", "-created_at"),
            Pair("Cũ nhất", "created_at"),
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

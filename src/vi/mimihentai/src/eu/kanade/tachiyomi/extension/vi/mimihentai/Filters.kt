package eu.kanade.tachiyomi.extension.vi.mimihentai

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
            Pair("Mới cập nhật", "-updated_at"),
            Pair("Xem nhiều", "-views"),
            Pair("A-Z", "name"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Đang tiến hành", "1"),
            Pair("Đã hoàn thành", "2"),
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

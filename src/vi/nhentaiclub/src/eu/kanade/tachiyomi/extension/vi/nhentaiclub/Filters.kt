package eu.kanade.tachiyomi.extension.vi.nhentaiclub

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        add(StatusFilter())
        add(SortFilter())
        genres?.takeIf { it.isNotEmpty() }?.let { options ->
            add(GenreFilter(options.map { Genre(it.name, it.name) }))
        }
    },
)

@Serializable
class GenreOption(
    val name: String,
    val slug: String,
)

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            Pair("Mới cập nhật", "recent-update"),
            Pair("Xem nhiều", "view"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            Pair("Tất cả", "all"),
            Pair("Hoàn thành", "completed"),
            Pair("Đang tiến hành", "progress"),
        ),
    )

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres) {
    fun selectedValues(): List<String> = state.filter { it.state }.map { it.value }
}

open class UriPartFilter(
    displayName: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = options[state].second
}

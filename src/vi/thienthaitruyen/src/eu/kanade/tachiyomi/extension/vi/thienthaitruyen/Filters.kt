package eu.kanade.tachiyomi.extension.vi.thienthaitruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        genres?.takeIf(List<GenreOption>::isNotEmpty)?.let { add(GenreFilter(it)) }
        add(StatusFilter())
        add(SortFilter())
    },
)

@Serializable
class GenreOption(
    val name: String,
    val value: String,
)

class UriPart(
    val displayName: String,
    val value: String?,
)

open class UriPartFilter(
    name: String,
    values: Array<UriPart>,
) : Filter.Select<String>(name, values.map(UriPart::displayName).toTypedArray()) {
    private val entries = values

    val selected: String?
        get() = entries[state].value
}

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<GenreOption>) : Filter.Group<Genre>("Thể loại", genres.map { Genre(it.name, it.value) })

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            UriPart("All", "all"),
            UriPart("Hoàn thành", "completed"),
            UriPart("Đang ra", "ongoing"),
            UriPart("Đang chờ xử lý", "pending"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sắp xếp theo",
        arrayOf(
            UriPart("Cập nhật gần đây", "latest"),
            UriPart("Xếp hạng", "rating"),
            UriPart("Số lượng đánh dấu", "bookmark"),
            UriPart("Tên (A-Z)", "name_asc"),
            UriPart("Tên (Z-A)", "name_desc"),
        ),
    )

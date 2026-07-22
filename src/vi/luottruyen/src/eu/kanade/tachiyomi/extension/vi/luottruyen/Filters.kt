package eu.kanade.tachiyomi.extension.vi.luottruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        genres
            ?.takeIf { it.isNotEmpty() }
            ?.let { add(GenreFilter(it)) }
        add(SortFilter())
        add(StatusFilter())
    },
)

@Serializable
class GenreOption(
    val name: String,
    val value: String,
)

class GenreFilter(genres: List<GenreOption>) : UriPartFilter("Thể loại", listOf(GenreOption("Tất cả", "")) + genres)

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        listOf(
            GenreOption("Mới cập nhật", "0"),
            GenreOption("Truyện mới", "15"),
            GenreOption("Top all", "10"),
            GenreOption("Top tháng", "11"),
            GenreOption("Top tuần", "12"),
            GenreOption("Top ngày", "13"),
            GenreOption("Theo dõi", "20"),
            GenreOption("Bình luận", "25"),
            GenreOption("Số chapter", "30"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        listOf(
            GenreOption("Tất cả", "-1"),
            GenreOption("Đang tiến hành", "1"),
            GenreOption("Hoàn thành", "2"),
        ),
    )

open class UriPartFilter(displayName: String, private val options: List<GenreOption>) : Filter.Select<String>(displayName, options.map { it.name }.toTypedArray()) {
    fun toUriPart(): String? = options[state].value.ifEmpty { null }
}

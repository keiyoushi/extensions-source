package eu.kanade.tachiyomi.extension.vi.daomeoden

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>? = null): FilterList {
    val filters = mutableListOf<Filter<*>>(
        StatusFilter(),
        CategoryFilter(),
    )
    if (!genres.isNullOrEmpty()) filters += GenreFilter(genres)
    filters += ExplicitFilter()
    filters += SortFilter()
    return FilterList(filters)
}

@Serializable
class GenreOption(
    val name: String,
    val id: String,
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

class GenreFilter(genres: List<GenreOption>) :
    UriPartFilter(
        "Genre",
        genres
            .map { it.name to it.id }
            .toTypedArray(),
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

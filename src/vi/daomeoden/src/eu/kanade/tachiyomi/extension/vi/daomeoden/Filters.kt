package eu.kanade.tachiyomi.extension.vi.daomeoden

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<FilterOption>? = null, tags: List<FilterOption>? = null): FilterList {
    val filters = mutableListOf<Filter<*>>(
        StatusFilter(),
        AdultFilter(),
        SortFilter(),
    )
    if (!genres.isNullOrEmpty()) filters += GenreFilter(genres)
    if (!tags.isNullOrEmpty()) filters += TagFilter(tags)
    return FilterList(filters)
}

class FilterOption(
    val name: String,
    val slug: String,
)

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Đang ra", "ongoing"),
            Pair("Hoàn thành", "completed"),
        ),
    )

class AdultFilter :
    UriPartFilter(
        "Explicit",
        arrayOf(
            Pair("All", ""),
            Pair("Bỏ qua 18+", "safe"),
            Pair("Chỉ 18+", "only18"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Order",
        arrayOf(
            Pair("Mới cập nhật", "latest"),
            Pair("Lượt xem nhiều", "views"),
            Pair("Đánh giá cao", "rating"),
            Pair("Tên truyện (A → Z)", "az"),
        ),
    )

class GenreFilter(options: List<FilterOption>) : CheckBoxGroupFilter("Thể Loại", options)

class TagFilter(options: List<FilterOption>) : CheckBoxGroupFilter("Tags Truyện", options)

open class CheckBoxGroupFilter(
    displayName: String,
    options: List<FilterOption>,
) : Filter.Group<CheckBoxFilter>(
    displayName,
    options.map { CheckBoxFilter(it.name, it.slug) },
) {
    fun included(): List<String> = state.filter { it.state }.map { it.slug }
}

class CheckBoxFilter(name: String, val slug: String) : Filter.CheckBox(name)

open class UriPartFilter(
    displayName: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = options[state].second
}

package eu.kanade.tachiyomi.extension.vi.hangtruyenonline

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement

fun getFilters(data: JsonElement?): FilterList {
    val filters = mutableListOf<Filter<*>>()

    val filterData = data?.parseAs<FilterData>()

    filterData?.categories?.takeIf { it.isNotEmpty() }?.let { categories ->
        filters.add(CategoryFilter(categories))
    }

    filterData?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
        filters.add(TagFilter(genres))
    }

    filters.add(SortFilter())

    return FilterList(filters)
}

class CategoryCheckBox(name: String, val id: String) : Filter.CheckBox(name)

class TagCheckBox(name: String, val id: String) : Filter.CheckBox(name)

class CategoryFilter(categories: List<CategoryOption>) :
    Filter.Group<CategoryCheckBox>(
        "Thể loại",
        categories.map { CategoryCheckBox(it.name, it.id) },
    ) {
    fun selectedIds(): String = state.filter { it.state }.joinToString(",") { it.id }
}

class TagFilter(genres: List<GenreOption>) :
    Filter.Group<TagCheckBox>(
        "Tags",
        genres.map { TagCheckBox(it.name, it.id) },
    ) {
    fun selectedIds(): String = state.filter { it.state }.joinToString(",") { it.id }
}

class SortFilter :
    Filter.Select<SortOption>(
        "Sắp xếp",
        arrayOf(
            SortOption("Lượt xem giảm dần", "view_desc"),
            SortOption("Lượt xem tăng dần", "view_asc"),
            SortOption("Ngày cập nhật giảm dần", "udpated_at_date_desc"),
            SortOption("Ngày cập nhật tăng dần", "udpated_at_date_asc"),
            SortOption("Ngày đăng giảm dần", "created_at_date_desc"),
            SortOption("Ngày đăng tăng dần", "created_at_date_asc"),
        ),
    ) {
    fun toUriPart(): String = values[state].value
}

class SortOption(val name: String, val value: String) {
    override fun toString() = name
}

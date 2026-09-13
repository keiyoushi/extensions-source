package eu.kanade.tachiyomi.extension.vi.otruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement

fun getFilters(data: JsonElement?): FilterList {
    val filters = mutableListOf<Filter<*>>(
        Filter.Header("Không dùng chung được với tìm kiếm bằng tên"),
    )

    data?.parseAs<DataDto<GenresData>>()?.data?.items?.takeIf { it.isNotEmpty() }?.let { items ->
        val genres = items.map { Genre(it.name, it.slug) }.sortedBy { it.name }
        filters.add(GenresFilter("Thể loại", genres))
    } ?: run {
        filters.add(StatusList())
    }

    return FilterList(filters)
}

class StatusList :
    Filter.Select<Genre>(
        "Trạng thái",
        arrayOf(
            Genre("Mới nhất", "truyen-moi"),
            Genre("Đang phát hành", "dang-phat-hanh"),
            Genre("Hoàn thành", "hoan-thanh"),
            Genre("Sắp ra mắt", "sap-ra-mat"),
        ),
    )

class GenresFilter(title: String, pairs: List<Genre>) :
    Filter.Select<Genre>(
        title,
        pairs.toTypedArray(),
    )

class Genre(val name: String, val slug: String) {
    override fun toString() = name
}

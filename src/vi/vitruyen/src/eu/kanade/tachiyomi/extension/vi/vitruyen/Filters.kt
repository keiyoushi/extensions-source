package eu.kanade.tachiyomi.extension.vi.vitruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(options: FilterOptions): FilterList = FilterList(
    buildList {
        add(Filter.Header("Bộ lọc sẽ bị bỏ qua khi tìm kiếm theo tên"))
        add(SortFilter())
        add(StatusFilter())
        options.categories.takeIf { it.isNotEmpty() }?.let { add(GenreFilter(it)) }
        options.translators.takeIf { it.isNotEmpty() }?.let { add(TranslatorFilter(it)) }
        options.schedules.takeIf { it.isNotEmpty() }?.let { add(ScheduleFilter(it)) }
    },
)

class SortFilter : Filter.Select<String>("Sắp xếp", arrayOf("Mới nhất", "Xem nhiều")) {
    private val sorts = listOf("latest", "view")

    fun toUriPart(): String = sorts[state]
}

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        listOf(
            Option("Đang ra", "ongoing"),
            Option("Hoàn thành", "completed"),
        ),
    )

class GenreFilter(options: List<Option>) : UriPartFilter("Thể loại", options)

class TranslatorFilter(options: List<Option>) : UriPartFilter("Nhóm dịch", options)

class ScheduleFilter(options: List<Option>) : UriPartFilter("Lịch chiếu", options)

open class UriPartFilter(
    displayName: String,
    options: List<Option>,
) : Filter.Select<String>(displayName, (listOf("Tất cả") + options.map { it.name }).toTypedArray()) {
    private val slugs = listOf<String?>(null) + options.map { it.slug }

    fun toUriPart(): String? = slugs[state]
}

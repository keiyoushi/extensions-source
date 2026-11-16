package eu.kanade.tachiyomi.multisrc.koi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

@Serializable
class FilterOption(
    val name: String,
    val path: String,
)

class UriPartFilter(
    name: String,
    options: List<FilterOption>,
) : Filter.Select<String>(name, (listOf("Tất cả") + options.map { it.name }).toTypedArray()) {
    private val paths = listOf(null) + options.map { it.path }

    fun toUriPart(): String? = paths[state]
}

fun getFilters(names: List<String>, options: List<List<FilterOption>>?): FilterList = FilterList(
    buildList {
        add(Filter.Header("Bộ lọc sẽ bị bỏ qua khi tìm kiếm"))
        options?.forEachIndexed { index, tabOptions ->
            if (tabOptions.isNotEmpty()) add(UriPartFilter(names[index], tabOptions))
        }
    },
)

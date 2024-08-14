package eu.kanade.tachiyomi.extension.ko.blacktoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

interface ListFilter {
    fun applyFilter(list: List<SeriesItem>): List<SeriesItem>
}

class TriFilter(name: String, val id: Int) : Filter.TriState(name)

abstract class TriFilterGroup(
    name: String,
    values: Map<Int, String>,
) : Filter.Group<TriFilter>(name, values.map { TriFilter(it.value, it.key) }), ListFilter {
    private val included get() = state.filter { it.isIncluded() }.map { it.id }
    private val excluded get() = state.filter { it.isExcluded() }.map { it.id }

    abstract fun SeriesItem.getAttribute(): List<Int>
    override fun applyFilter(list: List<SeriesItem>): List<SeriesItem> {
        return list.filter { series ->
            included.all {
                it in series.getAttribute()
            } and excluded.all {
                it !in series.getAttribute()
            }
        }
    }
}

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<Int, String>>,
) : Filter.Select<String>(
    name,
    options.map { it.second }.toTypedArray(),
) {

    val selected get() = options[state].first
}

class TagFilter : TriFilterGroup("Tag", tagsMap) {
    override fun SeriesItem.getAttribute(): List<Int> {
        return tag
    }
}

class PlatformFilter :
    SelectFilter(
        "Platform",
        buildList {
            add(-1 to "")
            platformsMap.forEach {
                add(it.key to it.value)
            }
        },
    ),
    ListFilter {
    override fun applyFilter(list: List<SeriesItem>): List<SeriesItem> {
        return list.filter { selected == -1 || it.platform == selected }
    }
}

class PublishDayFilter :
    SelectFilter(
        "Publishing Day",
        buildList {
            add(-1 to "")
            publishDayMap.forEach {
                add(it.key to it.value)
            }
        },
    ),
    ListFilter {
    override fun applyFilter(list: List<SeriesItem>): List<SeriesItem> {
        return list.filter { selected == -1 || it.publishDay == state }
    }
}

class Status :
    SelectFilter(
        "Status",
        listOf(
            -1 to "All",
            1 to "연재",
            0 to "완결",
        ),
    ),
    ListFilter {
    override fun applyFilter(list: List<SeriesItem>): List<SeriesItem> {
        return when (selected) {
            1, 0 -> list.filter { it.listIndex == selected }
            else -> list
        }
    }
}

class Order :
    SelectFilter(
        "Order by",
        listOf(
            0 to "최신순",
            1 to "인기순",
        ),
    ),
    ListFilter {
    override fun applyFilter(list: List<SeriesItem>): List<SeriesItem> {
        return when (selected) {
            0 -> list.sortedByDescending { it.updatedAt }
            1 -> list.sortedByDescending { it.hot }
            else -> list
        }
    }
}

fun getFilters() = FilterList(
    Order(),
    Status(),
    PlatformFilter(),
    PublishDayFilter(),
    TagFilter(),
)

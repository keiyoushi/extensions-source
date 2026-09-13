package eu.kanade.tachiyomi.extension.vi.tranh18

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class FilterOption(val name: String, val value: String) {
    override fun toString() = name
}

class GenreList(options: Array<FilterOption>) : Filter.Select<FilterOption>("Thể loại", options)

class StatusList(status: Array<FilterOption>) : Filter.Select<FilterOption>("Tiến độ", status)

class TagList(tags: Array<FilterOption>) : Filter.Select<FilterOption>("Từ khóa", tags)

@Serializable
class FilterData(
    val tags: List<FilterOption>,
    val areas: List<FilterOption>,
    val end: List<FilterOption>,
)

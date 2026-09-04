package eu.kanade.tachiyomi.extension.vi.cuutruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

@Serializable
class TagOption(val name: String, val slug: String)

class TagFilter(tags: List<TagOption>) :
    Filter.Group<TagCheckBox>(
        "Thể loại",
        tags.map { TagCheckBox(it.name, it.name) },
    ) {
    fun selectedNames(): List<String> = state.filter { it.state }.map { it.tagName }
}

class TagCheckBox(name: String, val tagName: String) : Filter.CheckBox(name)

fun getFilters(tags: List<TagOption>?): FilterList = if (tags.isNullOrEmpty()) {
    FilterList()
} else {
    FilterList(TagFilter(tags))
}

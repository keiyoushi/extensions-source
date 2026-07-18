package eu.kanade.tachiyomi.extension.pt.mangeek

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.firstInstanceOrNull

internal class TagFilter(name: String) : Filter.CheckBox(name)

internal class TagsFilter(tags: List<String>) :
    Filter.Group<TagFilter>(
        "Tags",
        tags.map(::TagFilter),
    )

internal fun FilterList.includedTags(): List<String> = firstInstanceOrNull<TagsFilter>()?.state
    ?.filter { it.state }
    ?.map { it.name }
    .orEmpty()

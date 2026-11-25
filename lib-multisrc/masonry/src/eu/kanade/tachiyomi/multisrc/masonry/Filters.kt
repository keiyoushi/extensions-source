package eu.kanade.tachiyomi.multisrc.masonry

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
) {
    val selected get() = options[state].second
}

class OrderFilter :
    SelectFilter(
        "Order",
        listOf(
            "Any Order" to "0",
            "Popular" to "popular",
            "Newest" to "newest",
        ),
    )

class QualityFilter :
    SelectFilter(
        "Quality",
        listOf(
            "All Quality" to "0",
            "HD" to "hd",
        ),
    )

@Serializable
class Tag(val name: String, val value: String)

class TagCheckBox(name: String, val value: String) : Filter.CheckBox(name)

class TagFilter(tags: List<Tag>) : Filter.Group<TagCheckBox>("Tags", tags.map { TagCheckBox(it.name, it.value) }) {
    val selected get() = state.filter { it.state }.map { it.value }
}

// Same field order as the site's filter panel
@Serializable
class GridFilter(
    val content: String,
    val quality: String,
    val label: String,
    val ord: String,
)

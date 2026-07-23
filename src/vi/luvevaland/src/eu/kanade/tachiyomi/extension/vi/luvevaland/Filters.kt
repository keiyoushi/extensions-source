package eu.kanade.tachiyomi.extension.vi.luvevaland

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

internal fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        if (!genres.isNullOrEmpty()) {
            add(TagFilter(genres.map { Tag(it.name, it.id) }))
        }
    },
)

@Serializable
internal class GenreOption(
    val name: String,
    val id: String,
)

internal class Tag(name: String, val id: String) : Filter.CheckBox(name)

internal class TagFilter(tags: List<Tag>) : Filter.Group<Tag>("Tag", tags)

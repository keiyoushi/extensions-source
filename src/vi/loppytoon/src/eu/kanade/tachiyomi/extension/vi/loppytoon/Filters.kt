package eu.kanade.tachiyomi.extension.vi.loppytoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(data: FilterData?): FilterList = FilterList(
    buildList {
        data?.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
            add(GenreFilter(genres.map { Genre(it.name, it.slug) }))
        }
        data?.groups?.takeIf { it.isNotEmpty() }?.let { groups ->
            add(GroupFilter(groups.map { ScanlationGroup(it.name, it.slug) }))
        }
    },
)

@Serializable
class FilterData(
    val genres: List<FilterOption>,
    val groups: List<FilterOption>,
)

@Serializable
class FilterOption(
    val name: String,
    val slug: String,
)

class Genre(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

class ScanlationGroup(name: String, val slug: String) : Filter.CheckBox(name)

class GroupFilter(groups: List<ScanlationGroup>) : Filter.Group<ScanlationGroup>("Nhóm dịch", groups)

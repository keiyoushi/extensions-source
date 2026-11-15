package eu.kanade.tachiyomi.extension.vi.loppytoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(groups: List<FilterGroupData>?): FilterList = FilterList(
    buildList {
        add(SortFilter())
        add(ExcludeAdultFilter())
        if (!groups.isNullOrEmpty()) {
            for (group in groups) {
                add(GenreGroup(group.name, group.options.map { Genre(it.name, it.id) }))
            }
        }
    },
)

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    defaultValue: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultValue) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sắp xếp theo",
        arrayOf(
            "Mới nhất" to "newest",
            "Xem nhiều nhất" to "views",
        ),
    )

class ExcludeAdultFilter : Filter.CheckBox("Loại trừ 19+")

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreGroup(name: String, genres: List<Genre>) : Filter.Group<Genre>(name, genres)

@Serializable
class FilterGroupData(
    val name: String,
    val options: List<FilterOptionData> = emptyList(),
)

@Serializable
class FilterOptionData(
    val name: String,
    val id: String,
)

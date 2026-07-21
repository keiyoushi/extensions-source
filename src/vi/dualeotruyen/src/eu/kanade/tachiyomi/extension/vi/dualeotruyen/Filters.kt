package eu.kanade.tachiyomi.extension.vi.dualeotruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        genres?.let {
            add(GenreFilter(listOf(GenreOption("Tất cả", "")) + it))
        }
    },
)

@Serializable
class GenreOption(
    val name: String,
    val uriPart: String,
)

class GenreFilter(genres: List<GenreOption>) : UriPartFilter("Thể loại", genres)

open class UriPartFilter(
    displayName: String,
    private val genres: List<GenreOption>,
) : Filter.Select<String>(displayName, genres.map { it.name }.toTypedArray()) {
    fun toUriPart(): String? = genres[state].uriPart.ifEmpty { null }
}

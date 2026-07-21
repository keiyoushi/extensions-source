package eu.kanade.tachiyomi.extension.vi.kamicomic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        genres
            ?.takeIf { it.isNotEmpty() }
            ?.let { add(GenreFilter(listOf(GenreOption("Tất cả", "")) + it)) }
    },
)

@Serializable
class GenreOption(
    val name: String,
    val slug: String,
)

class GenreFilter(
    private val genres: List<GenreOption>,
) : Filter.Select<String>("Thể loại", genres.map { it.name }.toTypedArray()) {
    fun selectedSlug(): String? = genres[state].slug.ifEmpty { null }
}

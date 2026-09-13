package eu.kanade.tachiyomi.extension.vi.truyenhentaivn

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        genres?.takeIf { it.isNotEmpty() }?.let { add(GenreFilter(it)) }
    },
)

@Serializable
class GenreOption(
    val name: String,
    val path: String,
)

class GenreFilter(genres: List<GenreOption>) :
    Filter.Select<String>(
        "Thể loại Hentai",
        arrayOf("Tất cả", *genres.map { it.name }.toTypedArray()),
    ) {
    private val paths = listOf<String?>(null) + genres.map { it.path }

    fun toUriPart(): String? = paths[state]
}

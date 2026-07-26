package eu.kanade.tachiyomi.extension.vi.truyenhentai18

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        genres
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                add(Filter.Header("Lưu ý: Bộ lọc thể loại chỉ hoạt động khi ô tìm kiếm trống"))
                add(GenreFilter(it))
            }
    },
)

@Serializable
class GenreOption(
    val name: String,
    val slug: String,
)

class GenreFilter(genres: List<GenreOption>) :
    Filter.Select<String>(
        "Thể loại",
        genres.map { it.name }.toTypedArray(),
    ) {
    private val slugs = genres.map { it.slug }

    fun toUriPart(): String = slugs[state]
}

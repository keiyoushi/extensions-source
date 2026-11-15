package eu.kanade.tachiyomi.extension.vi.hentaivnx

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        genres
            ?.takeIf { it.isNotEmpty() }
            ?.let { add(GenreFilter(it.map { genre -> Genre(genre.name, genre.id) })) }
        add(SortByList())
        add(ChapterCountList())
        add(TextField())
    },
)

@Serializable
class GenreOption(
    val name: String,
    val id: String,
)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

class Genre(name: String, val genre: String) : Filter.TriState(name) {
    override fun toString() = name
}

class TextField : Filter.Text("Tên thể loại có chứa", "")

class SortByList(state: Int = 0) :
    Filter.Select<Genre>(
        "Sắp xếp theo",
        arrayOf(
            Genre("Truyện mới", "15"),
            Genre("Top all", "10"),
            Genre("Top tháng", "11"),
            Genre("Top tuần", "12"),
            Genre("Top ngày", "13"),
            Genre("Theo dõi", "20"),
            Genre("Bình luận", "25"),
            Genre("Số chapter", "30"),
            Genre("Top Follow", "19"),
        ),
        state,
    )

class ChapterCountList :
    Filter.Select<Genre>(
        "Số lượng chapter",
        arrayOf(
            Genre(">= 0 chapters", "0"),
            Genre(">= 5 chapters", "5"),
            Genre(">= 10 chapters", "10"),
            Genre(">= 20 chapters", "20"),
            Genre(">= 30 chapters", "30"),
            Genre(">= 50 chapters", "50"),
        ),
    )

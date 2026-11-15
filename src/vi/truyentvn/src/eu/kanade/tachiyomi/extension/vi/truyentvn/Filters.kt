package eu.kanade.tachiyomi.extension.vi.truyentvn

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(data: FilterData?): FilterList = FilterList(
    buildList {
        add(CountryFilter())
        add(StatusFilter())
        add(SortFilter())
        add(AgeRatingFilter())
        add(ChapterRangeFilter())
        data?.categories?.takeIf { it.isNotEmpty() }?.let { add(CategoryFilter(it)) }
        data?.genres?.takeIf { it.isNotEmpty() }?.let { add(GenreFilter(it)) }
    },
)

@Serializable
class FilterData(
    val categories: List<FilterOption>,
    val genres: List<FilterOption>,
)

@Serializable
class FilterOption(
    val name: String,
    val slug: String,
)

class CountryFilter :
    UriPartFilter(
        "Quốc gia",
        arrayOf(
            "Tất cả" to "",
            "Nhật Bản" to "nhat-ban",
            "Trung Quốc" to "trung-quoc",
            "Hàn Quốc" to "han-quoc",
            "Việt Nam" to "viet-nam",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            "Tất cả" to "",
            "Đang tiến hành" to "ongoing",
            "Đã hoàn thành" to "completed",
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            "Mới cập nhật" to "date",
            "Xem nhiều nhất" to "views",
            "Tiêu đề A-Z" to "title",
            "Yêu thích nhiều nhất" to "favorites",
        ),
    )

class AgeRatingFilter :
    UriPartFilter(
        "Độ tuổi",
        arrayOf(
            "Tất cả" to "",
            "Không 18+" to "non_18",
            "18+ (Người lớn)" to "is_18",
        ),
    )

class ChapterRangeFilter :
    UriPartFilter(
        "Số chương",
        arrayOf(
            "Tất cả" to "",
            "Một chương (Oneshot)" to "one",
            "2 - 10 chương" to "two_ten",
            "11 - 50 chương" to "eleven_fifty",
            "Nhiều hơn 50 chương" to "fifty_plus",
        ),
    )

class CategoryFilter(categories: List<FilterOption>) :
    UriPartFilter(
        "Danh mục",
        (listOf("Tất cả" to "") + categories.map { it.name to it.slug }).toTypedArray(),
    )

class Genre(name: String, val slug: String) : Filter.TriState(name)

class GenreFilter(genres: List<FilterOption>) : Filter.Group<Genre>("Thể loại", genres.map { Genre(it.name, it.slug) })

open class UriPartFilter(
    displayName: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = options[state].second
}

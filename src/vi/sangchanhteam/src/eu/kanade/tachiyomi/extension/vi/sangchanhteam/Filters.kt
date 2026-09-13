package eu.kanade.tachiyomi.extension.vi.sangchanhteam

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        add(TypeFilter())
        add(StatusFilter())
        add(AgeRatingFilter())
        add(SortFilter())
        genres?.takeIf { it.isNotEmpty() }?.let { options ->
            add(GenreFilter(options.map { Genre(it.name, it.slug) }))
        }
    },
)

class TypeFilter :
    UriPartFilter(
        "Loại truyện",
        arrayOf(
            "Tất cả" to "",
            "Truyện tranh" to "comic",
            "Tiểu thuyết" to "novel",
            "Oneshot" to "oneshot",
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            "Tất cả" to "",
            "Đang tiến hành" to "ongoing",
            "Kết thúc mùa" to "season_end",
            "Trọn bộ" to "completed",
            "Nguồn tạm ngưng" to "source_hiatus",
            "Đã theo kịp" to "caught_up",
            "Bị hủy" to "dropped",
        ),
    )

class AgeRatingFilter :
    UriPartFilter(
        "Độ tuổi",
        arrayOf(
            "Tất cả" to "",
            "Mọi lứa tuổi" to "all",
            "13+" to "13+",
            "16+" to "16+",
            "18+" to "18+",
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            "Mới cập nhật" to "updated",
            "Mới nhất" to "new",
            "Cũ nhất" to "old",
            "Nhiều lượt xem nhất" to "views",
            "Lượt xem hôm nay" to "views_day",
            "Lượt xem tuần này" to "views_week",
            "Lượt xem tháng này" to "views_month",
            "Đánh giá cao nhất" to "rating",
            "Nhiều Thần Chú nhất" to "power",
            "Nhiều người theo dõi nhất" to "follow",
        ),
    )

class Genre(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

open class UriPartFilter(
    displayName: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
    fun toUriPart(): String = options[state].second
}

package eu.kanade.tachiyomi.extension.vi.meosss

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

@Serializable
class GenreOption(
    val name: String,
    val value: String,
)

class Genre(option: GenreOption) : Filter.CheckBox(option.name) {
    val value = option.value
}

class GenreFilter(genres: List<GenreOption>) : Filter.Group<Genre>("Thể loại", genres.map(::Genre))

class StatusFilter :
    Filter.Select<String>(
        "Tình trạng",
        arrayOf(
            "Tất cả",
            "Đang tiến hành",
            "Kết thúc mùa",
            "Trọn bộ",
            "Nguồn tạm ngưng",
            "Đã theo kịp",
            "Bị hủy",
        ),
    )

class AgeRatingFilter :
    Filter.Select<String>(
        "Độ tuổi",
        arrayOf(
            "Tất cả",
            "Mọi lứa tuổi",
            "13+",
            "16+",
            "18+",
        ),
    )

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp",
        arrayOf(
            "Mới cập nhật",
            "Mới nhất",
            "Cũ nhất",
            "Nhiều lượt xem nhất",
            "Lượt xem hôm nay",
            "Lượt xem tuần này",
            "Lượt xem tháng này",
            "Đánh giá cao nhất",
            "Opal nhiều nhất",
            "Nhiều người theo dõi nhất",
        ),
    )

fun getFilters(genres: List<GenreOption>): FilterList = FilterList(
    buildList {
        if (genres.isNotEmpty()) add(GenreFilter(genres))
        add(StatusFilter())
        add(AgeRatingFilter())
        add(SortFilter())
    },
)

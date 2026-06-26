package eu.kanade.tachiyomi.extension.vi.meosss

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

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

fun getFilters(genres: List<Genre>): FilterList = FilterList(
    if (genres.isEmpty()) {
        Filter.Header("Nhấn 'Làm mới' để tải thể loại")
    } else {
        GenreFilter(genres)
    },
    StatusFilter(),
    AgeRatingFilter(),
    SortFilter(),
)

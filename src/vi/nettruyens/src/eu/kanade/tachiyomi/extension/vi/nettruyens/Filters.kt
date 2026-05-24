package eu.kanade.tachiyomi.extension.vi.nettruyens

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val id: String) : Filter.TriState(name)

class GenreGroupFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        STATUS_LIST.map { it.first }.toTypedArray(),
    ) {
    fun toValue() = STATUS_LIST[state].second
}

class GenderFilter :
    Filter.Select<String>(
        "Dành cho",
        GENDER_LIST.map { it.first }.toTypedArray(),
    ) {
    fun toValue() = GENDER_LIST[state].second
}

class MinChapterFilter :
    Filter.Select<String>(
        "Số chapter tối thiểu",
        MIN_CHAPTER_LIST.map { it.first }.toTypedArray(),
    ) {
    fun toValue() = MIN_CHAPTER_LIST[state].second
}

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp",
        SORT_LIST.map { it.first }.toTypedArray(),
    ) {
    fun toValue() = SORT_LIST[state].second
}

private val STATUS_LIST = listOf(
    "Tất cả" to "",
    "Hoàn thành" to "completed",
    "Đang tiến hành" to "on-going",
    "Tạm ngưng" to "on-hold",
    "Đã hủy" to "canceled",
)

private val GENDER_LIST = listOf(
    "Tất cả" to "All",
    "Con trai" to "Boy",
    "Con gái" to "Girl",
)

private val MIN_CHAPTER_LIST = listOf(
    ">= 0 chapter" to "0",
    ">= 10 chapter" to "10",
    ">= 20 chapter" to "20",
    ">= 30 chapter" to "30",
    ">= 40 chapter" to "40",
    ">= 50 chapter" to "50",
    ">= 100 chapter" to "100",
    ">= 200 chapter" to "200",
    ">= 300 chapter" to "300",
    ">= 400 chapter" to "400",
    ">= 500 chapter" to "500",
)

private val SORT_LIST = listOf(
    "Mới cập nhật" to "latest-updated",
    "Xem nhiều nhất" to "views",
    "Xem nhiều nhất tháng" to "views_month",
    "Xem nhiều nhất tuần" to "views_week",
    "Xem nhiều nhất ngày" to "views_day",
    "Đánh giá cao" to "score",
    "Từ A-Z" to "az",
    "Từ Z-A" to "za",
    "Số chương nhiều nhất" to "chapters",
    "Mới nhất" to "new",
    "Cũ nhất" to "old",
)

fun getGenreList() = listOf(
    Genre("Action", "16"),
    Genre("Adventure", "17"),
    Genre("AI", "4109"),
    Genre("Anime", "84"),
    Genre("Chuyển Sinh", "12"),
    Genre("Cổ Đại", "19"),
    Genre("Cổ Trang", "3365"),
    Genre("Comedy", "6"),
    Genre("Comic", "247"),
    Genre("Crossdress", "4111"),
    Genre("Demons", "213"),
    Genre("Detective", "71"),
    Genre("Doujinshi", "151"),
    Genre("Drama", "14"),
    Genre("Đam Mỹ", "85"),
    Genre("Ecchi", "5"),
    Genre("Fantasy", "7"),
    Genre("Gender Bender", "93"),
    Genre("Harem", "4"),
    Genre("Historical", "47"),
    Genre("Horror", "50"),
    Genre("Huyền Huyễn", "59"),
    Genre("Isekai", "21"),
    Genre("Josei", "78"),
    Genre("Magic", "26"),
    Genre("Manga", "4110"),
    Genre("Manhua", "8"),
    Genre("Manhwa", "13"),
    Genre("Martial Arts", "23"),
    Genre("Mature", "88"),
    Genre("Mystery", "30"),
    Genre("Ngôn Tình", "11"),
    Genre("Novel", "6559"),
    Genre("One Shot", "150"),
    Genre("Psychological", "68"),
    Genre("Romance", "2"),
    Genre("School Life", "15"),
    Genre("Sci-Fi", "69"),
    Genre("Seinen", "45"),
    Genre("Shoujo", "10"),
    Genre("Shoujo Ai", "255"),
    Genre("Shounen", "22"),
    Genre("Shounen Ai", "32"),
    Genre("Slice Of Life", "3"),
    Genre("Sports", "65"),
    Genre("Supernatural", "20"),
    Genre("Tragedy", "70"),
    Genre("Trọng Sinh", "58"),
    Genre("Truyện Màu", "9"),
    Genre("Webtoon", "24"),
    Genre("Xuyên Không", "18"),
    Genre("Yaoi", "120"),
    Genre("Yuri", "40"),
)

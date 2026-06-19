package eu.kanade.tachiyomi.extension.vi.ariverse

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class GenreFilter(
    name: String,
    genres: List<Pair<String, String>>,
) : Filter.Group<CheckBoxFilter>(
    name,
    genres.map { CheckBoxFilter(it.first, it.second) },
) {
    fun selectedValues(): List<String> = state.filter { it.state }.map { it.value }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf(
            "All",
            "Ongoing",
            "Completed",
        ),
    ) {
    fun toUriPart(): String? = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        else -> null
    }
}

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        arrayOf(
            "Relevance",
            "Latest update",
            "Newest published",
            "Title A–Z",
            "Title Z–A",
            "Recently added",
        ),
    ) {
    fun toSortValue(): String? = when (state) {
        1 -> "updated_at"
        2 -> "published_at"
        3 -> "title"
        4 -> "title"
        5 -> "created_at"
        else -> null
    }

    fun toOrderValue(): String? = when (state) {
        4 -> "asc"
        else -> if (toSortValue() != null) "desc" else null
    }
}

private val GENRE_LIST = listOf(
    "18+" to "18",
    "Âu Cổ" to "au-co",
    "Cách biệt tuổi tác" to "cach-biet-tuoi-tac",
    "Chiếm hữu" to "chiem-huu",
    "Cổ trang" to "co-trang",
    "Drama" to "drama",
    "Fantasy" to "fantasy",
    "Girl Crush" to "girl-crush",
    "Hài hước" to "hai-huoc",
    "Học đường" to "hoc-duong",
    "Horror" to "horror",
    "Idol" to "idol",
    "Mafia" to "mafia",
    "Manhua" to "manhua",
    "Manhwa" to "manhwa",
    "Mọt sách" to "mot-sach",
    "Mùa hè và biển cả" to "mua-he-va-bien-ca",
    "Người lớn" to "nguoi-lon",
    "Nhẹ nhàng" to "nhe-nhang",
    "One-shot" to "one-shot",
    "Quan hệ Chủ-Tớ" to "quan-he-chu-to",
    "Thanh Xuân Mùa Hạ" to "thanh-xuan-mua-ha",
    "Tình tay ba" to "tinh-tay-ba",
    "Tội phạm" to "toi-pham",
    "Trả thù" to "tra-thu",
    "Truyện Tranh" to "truyen-tranh",
    "Tuyết Đầu Mùa" to "tuyet-dau-mua",
    "Văn phòng" to "van-phong",
    "Xã hội đen" to "xa-hoi-den",
)

fun getFilters(): FilterList = FilterList(
    GenreFilter("Genre", GENRE_LIST),
    StatusFilter(),
    SortFilter(),
)

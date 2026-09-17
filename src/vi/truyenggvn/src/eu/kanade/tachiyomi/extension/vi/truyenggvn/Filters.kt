package eu.kanade.tachiyomi.extension.vi.truyenggvn

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
class GenreDto(
    val name: String,
    val id: String,
)

class Genre(name: String, val id: String) : Filter.TriState(name) {
    override fun toString(): String = name
}

class CountryFilter :
    Filter.Select<String>(
        "Quốc gia",
        options.map { it.first }.toTypedArray(),
    ) {
    val selected get() = options[state].second

    companion object {
        private val options = arrayOf(
            Pair("Tất cả", "0"),
            Pair("Trung Quốc", "1"),
            Pair("Việt Nam", "2"),
            Pair("Hàn Quốc", "3"),
            Pair("Nhật Bản", "4"),
            Pair("Mỹ", "5"),
        )
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Tình trạng",
        options.map { it.first }.toTypedArray(),
    ) {
    val selected get() = options[state].second

    companion object {
        private val options = arrayOf(
            Pair("Tất cả", "-1"),
            Pair("Đang tiến hành", "0"),
            Pair("Hoàn thành", "2"),
        )
    }
}

class ChapterCountFilter :
    Filter.Select<String>(
        "Số lượng chương",
        options.map { it.first }.toTypedArray(),
    ) {
    val selected get() = options[state].second

    companion object {
        private val options = arrayOf(
            Pair("> 0", "0"),
            Pair(">= 50", "50"),
            Pair(">= 100", "100"),
            Pair(">= 200", "200"),
            Pair(">= 300", "300"),
            Pair(">= 400", "400"),
            Pair(">= 500", "500"),
        )
    }
}

class SortByFilter :
    Filter.Sort(
        "Sắp xếp",
        arrayOf("Ngày đăng", "Ngày cập nhật", "Lượt xem"),
        Selection(1, ascending = false),
    )

class GenreList(state: List<Genre>) : Filter.Group<Genre>("Thể loại", state)

package eu.kanade.tachiyomi.extension.vi.lxhentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>?): FilterList = FilterList(
    buildList {
        add(SearchTypeFilter())
        add(SortFilter())
        add(StatusFilter())
        if (!genres.isNullOrEmpty()) {
            add(Filter.Separator())
            add(Filter.Header("Thể loại: Nhấn 1 lần để bao gồm, nhấn 2 lần để loại trừ"))
            add(GenreFilter(genres.map { genre -> Genre(genre.name, genre.slug) }))
        }
    },
)

@Serializable
class GenreOption(
    val name: String,
    val slug: String,
)

class SearchTypeFilter :
    Filter.Select<String>(
        "Tìm theo",
        arrayOf("Tên truyện", "Tác giả", "Doujinshi"),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "artist"
        2 -> "doujinshi"
        else -> "name"
    }
}

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp",
        arrayOf("Mới cập nhật", "Mới nhất", "Cũ nhất", "Xem nhiều", "A-Z", "Z-A"),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "-created_at"
        2 -> "created_at"
        3 -> "-views"
        4 -> "name"
        5 -> "-name"
        else -> "-updated_at"
    }
}

class StatusFilter :
    Filter.Group<StatusOption>(
        "Tình trạng",
        listOf(
            StatusOption("Đang tiến hành", "ongoing", true),
            StatusOption("Hoàn thành", "completed", true),
            StatusOption("Tạm dừng", "paused", true),
        ),
    )

class StatusOption(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)

fun StatusFilter.selectedValues(): List<String> = state
    .filter { option -> option.state }
    .map { option -> option.value }

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

class Genre(name: String, val slug: String) : Filter.TriState(name) {
    override fun toString(): String = name
}

fun GenreFilter.includedValues(): List<String> = state
    .filter { genre -> genre.state == Filter.TriState.STATE_INCLUDE }
    .map { genre -> genre.slug }

fun GenreFilter.excludedValues(): List<String> = state
    .filter { genre -> genre.state == Filter.TriState.STATE_EXCLUDE }
    .map { genre -> genre.slug }

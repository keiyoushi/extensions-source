package eu.kanade.tachiyomi.extension.vi.cmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun getFilters(genres: List<GenreOption>, teams: List<FilterOption>): FilterList {
    val filters = mutableListOf<Filter<*>>()
    if (genres.isNotEmpty()) filters += GenreFilter(genres.map { Genre(it.name, it.value) })
    if (teams.isNotEmpty()) filters += TeamFilter(teams)
    filters += MinChapterFilter()
    filters += SortFilter()
    filters += StatusFilter()
    return FilterList(filters)
}

@Serializable
data class CMangaFilterData(
    val genres: List<GenreOption>,
    val teams: List<FilterOption> = emptyList(),
)

@Serializable
data class CMangaGenreResponse(val list: Map<String, CMangaGenre>)

@Serializable
data class CMangaGenre(val name: String)

@Serializable
data class CMangaTeamResponse(val data: List<CMangaTeam>)

@Serializable
data class CMangaTeam(
    @SerialName("id_team") val id: Int,
    val info: String,
)

@Serializable
data class CMangaTeamInfo(val name: String)

@Serializable
data class FilterOption(val name: String, val value: String)

typealias GenreOption = FilterOption

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres) {
    fun selectedValues(): List<String> = state.filter { it.state }.map { it.value }
}

class TeamFilter(teams: List<FilterOption>) :
    UriPartFilter(
        "Nhóm dịch",
        listOf(FilterOption("Tất cả", "0")) + teams,
    )

class MinChapterFilter : UriPartFilter("Số chapter tối thiểu", MIN_CHAPTER_OPTIONS.toFilterOptions())

class SortFilter : UriPartFilter("Sắp xếp theo", SORT_OPTIONS.toFilterOptions())

class StatusFilter : UriPartFilter("Tình trạng", STATUS_OPTIONS.toFilterOptions())

open class UriPartFilter(displayName: String, private val options: List<FilterOption>) : Filter.Select<String>(displayName, options.map { it.name }.toTypedArray()) {

    fun toUriPart(): String = options[state].value
}

private fun Array<Pair<String, String>>.toFilterOptions() = map { FilterOption(it.first, it.second) }

private val MIN_CHAPTER_OPTIONS = arrayOf(
    "0 chapter" to "0",
    "20 chapter" to "20",
    "40 chapter" to "40",
    "60 chapter" to "60",
    "80 chapter" to "80",
    "100 chapter" to "100",
    "120 chapter" to "120",
    "140 chapter" to "140",
    "160 chapter" to "160",
    "180 chapter" to "180",
    "200 chapter" to "200",
    "220 chapter" to "220",
    "240 chapter" to "240",
    "260 chapter" to "260",
    "280 chapter" to "280",
    "300 chapter" to "300",
    "320 chapter" to "320",
    "340 chapter" to "340",
    "360 chapter" to "360",
    "380 chapter" to "380",
    "400 chapter" to "400",
    "420 chapter" to "420",
    "440 chapter" to "440",
    "460 chapter" to "460",
    "480 chapter" to "480",
    "500 chapter" to "500",
    "520 chapter" to "520",
    "540 chapter" to "540",
    "560 chapter" to "560",
    "580 chapter" to "580",
    "600 chapter" to "600",
    "620 chapter" to "620",
    "640 chapter" to "640",
    "660 chapter" to "660",
    "680 chapter" to "680",
    "700 chapter" to "700",
    "720 chapter" to "720",
    "740 chapter" to "740",
    "760 chapter" to "760",
    "780 chapter" to "780",
    "800 chapter" to "800",
    "820 chapter" to "820",
    "840 chapter" to "840",
    "860 chapter" to "860",
    "880 chapter" to "880",
    "900 chapter" to "900",
    "920 chapter" to "920",
    "940 chapter" to "940",
    "960 chapter" to "960",
    "980 chapter" to "980",
    "1000 chapter" to "1000",
)

private val SORT_OPTIONS = arrayOf(
    "Thời gian đăng" to "update",
    "Lượt xem trong ngày" to "view_day",
    "Lượt xem trong tuần" to "view_week",
    "Lượt xem trong tháng" to "view_month",
    "Lượt xem tổng" to "view_total",
    "Lượt follow" to "follow",
)

private val STATUS_OPTIONS = arrayOf(
    "Tất cả" to "all",
    "Đang ra" to "doing",
    "Đã dừng" to "drop",
    "Đã hoàn thành" to "done",
)

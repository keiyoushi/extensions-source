package eu.kanade.tachiyomi.extension.pt.taimumangas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SeriesListResponse(
    @JsonNames("data")
    val series: List<SeriesSummary> = emptyList(),
    val pagination: Pagination = Pagination(),
    @SerialName("total_series") val totalSeries: Int = 0,
)

@Serializable
data class SeriesSummary(
    val id: String = "",
    val code: String = "",
    val title: String = "",
    val cover: String? = null,
    val status: String? = null,
    val year: Int? = null,
    @SerialName("total_likes") val totalLikes: Int = 0,
    @SerialName("bookmark_count") val bookmarkCount: Int = 0,
    val rating: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Pagination(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_items") val totalItems: Int = 0,
    @SerialName("has_next") val hasNext: Boolean = false,
    @SerialName("has_previous") val hasPrevious: Boolean = false,
)

@Serializable
data class SeriesDetailResponse(
    val message: String = "",
    val series: SeriesDetail,
)

@Serializable
data class SeriesDetail(
    val id: String = "",
    val title: String = "",
    val country: String? = null,
    val code: String = "",
    val cover: String? = null,
    val status: String? = null,
    val synopsis: String? = null,
    @SerialName("release_year") val releaseYear: Int? = null,
    @SerialName("alternative_names") val alternativeNames: String? = null,
    @SerialName("total_bookmarks") val totalBookmarks: Int = 0,
    @SerialName("total_read_later") val totalReadLater: Int = 0,
    @SerialName("average_rating") val averageRating: Double? = null,
    @SerialName("total_ratings") val totalRatings: Int = 0,
    val recommendation: Int = 0,
    val author: NameCode? = null,
    val artist: NameCode? = null,
    val authors: List<NameCode> = emptyList(),
    val artists: List<NameCode> = emptyList(),
    val group: GroupInfo? = null,
    val genres: List<Genre> = emptyList(),
)

@Serializable
data class NameCode(
    val name: String = "",
    val code: String = "",
)

@Serializable
data class GroupInfo(
    val name: String = "",
    val slug: String = "",
    val code: String = "",
    val avatar: String? = null,
)

@Serializable
data class Genre(
    val id: String = "",
    val name: String = "",
)

@Serializable
data class ChapterListResponse(
    val data: ChapterListData = ChapterListData(),
    val message: String = "",
)

@Serializable
data class ChapterListData(
    val chapters: List<ChapterSummary> = emptyList(),
    @SerialName("total_chapters") val totalChapters: Int = 0,
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("has_next") val hasNext: Boolean = false,
    @SerialName("has_previous") val hasPrevious: Boolean = false,
    @SerialName("per_page") val perPage: Int = 0,
    @SerialName("first_chapter") val firstChapter: ChapterPointer? = null,
    @SerialName("last_chapter") val lastChapter: ChapterPointer? = null,
)

@Serializable
data class ChapterSummary(
    val number: JsonElement = JsonPrimitive(""),
    val code: String = "",
    val title: String? = null,
    val thumbnail: String? = null,
    val season: Int = 1,
    @SerialName("total_likes") val totalLikes: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val read: Boolean = false,
) {
    val numberText: String
        get() = number.jsonPrimitive.contentOrNull.orEmpty()
}

@Serializable
data class ChapterPointer(
    val number: String = "",
    val code: String = "",
)

@Serializable
data class ChapterDetailResponse(
    val chapter: ChapterDetail,
    val message: String = "",
)

@Serializable
data class ChapterDetail(
    @SerialName("series_code") val seriesCode: String = "",
    @SerialName("series_title") val seriesTitle: String = "",
    @SerialName("chapter_number") val chapterNumber: String = "",
    @SerialName("chapter_title") val chapterTitle: String? = null,
    @SerialName("chapter_code") val chapterCode: String = "",
    val pages: List<PageInfo> = emptyList(),
)

@Serializable
data class PageInfo(
    val path: String = "",
    val number: Int = 0,
)

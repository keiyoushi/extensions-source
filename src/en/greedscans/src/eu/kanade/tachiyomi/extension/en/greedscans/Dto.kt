package eu.kanade.tachiyomi.extension.en.greedscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SeriesListResponse(
    val data: PaginatedData,
)

@Serializable
class PaginatedData(
    val data: List<BrowseSeries>,
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
) {
    fun hasNextPage() = currentPage < lastPage
}

@Serializable
class BrowseSeries(
    val title: String,
    val slug: String,
    @SerialName("cover_image") val coverImage: String? = null,
    val status: String? = null,
)

@Serializable
class SeriesDetailResponse(
    val data: SeriesDetail,
)

@Serializable
class SeriesDetail(
    val title: String,
    val slug: String,
    val synopsis: String? = null,
    val author: String? = null,
    val studio: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    @SerialName("alternative_titles") val alternativeTitles: List<String> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
)

@Serializable
class Chapter(
    val title: String,
    val slug: String,
    @SerialName("chapter_number") val chapterNumber: Float,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
class ChapterDetailResponse(
    val data: ChapterDetail,
)

@Serializable
class ChapterDetail(
    val chapter: ChapterImages,
)

@Serializable
class ChapterImages(
    val images: List<PageImage> = emptyList(),
)

@Serializable
class PageImage(
    @SerialName("image_url") val imageUrl: String,
)

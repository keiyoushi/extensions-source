package eu.kanade.tachiyomi.extension.zh.kuaikanmanhua

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class WebSearchPayload(val data: List<WebSearchData> = emptyList())

@Serializable
internal data class WebSearchData(val dataList: List<WebManga> = emptyList())

@Serializable
internal data class WebManga(
    val id: Int,
    val title: String,
    @SerialName("vertical_image_url") val verticalImageUrl: String,
)

@Serializable
internal data class ApiSearchResponse(
    val data: ApiSearchData? = null,
)

@Serializable
internal data class ApiSearchData(
    val hit: List<ApiManga>? = null,
    val since: Int = -1,
)

@Serializable
internal data class ApiManga(
    val id: Int,
    val title: String,
    @SerialName("vertical_image_url") val verticalImageUrl: String,
)

@Serializable
internal data class WebMangaPayload(val data: List<WebMangaData> = emptyList())

@Serializable
internal data class WebMangaData(
    val topicInfo: WebMangaDetails,
    val comicList: List<WebMangaChapter> = emptyList(),
)

@Serializable
internal data class WebMangaDetails(
    val title: String,
    @SerialName("vertical_image_url") val verticalImageUrl: String,
    val user: WebAuthor,
    val description: String,
    @SerialName("update_status") val updateStatus: String,
)

@Serializable
internal data class WebAuthor(val nickname: String)

@Serializable
internal data class WebMangaChapter(
    val id: Int,
    val title: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
internal data class WebChapterPayload(val data: List<WebChapterData> = emptyList())

@Serializable
internal data class WebChapterData(val res: WebChapterResponse)

@Serializable
internal data class WebChapterResponse(val data: WebChapterResponseData)

@Serializable
internal data class WebChapterResponseData(
    @SerialName("comic_info") val comicInfo: WebChapter,
)

@Serializable
internal data class WebChapter(
    @SerialName("comic_images") val comicImages: List<WebPage> = emptyList(),
)

@Serializable
internal data class WebPage(val url: String)

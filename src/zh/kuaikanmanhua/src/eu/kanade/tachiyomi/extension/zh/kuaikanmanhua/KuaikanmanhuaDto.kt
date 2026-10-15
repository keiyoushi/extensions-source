package eu.kanade.tachiyomi.extension.zh.kuaikanmanhua

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal class WebSearchPayload(val data: List<WebSearchData> = emptyList())

@Serializable
internal class WebSearchData(val dataList: List<WebManga> = emptyList())

@Serializable
internal class WebManga(
    val id: Int,
    val title: String,
    @SerialName("vertical_image_url") val verticalImageUrl: String,
)

@Serializable
internal class ApiSearchResponse(
    val data: ApiSearchData? = null,
)

@Serializable
internal class ApiSearchData(
    val hit: List<ApiManga>? = null,
    val since: Int = -1,
)

@Serializable
internal class ApiManga(
    val id: Int,
    val title: String,
    @SerialName("vertical_image_url") val verticalImageUrl: String,
)

@Serializable
internal class WebMangaPayload(val data: List<WebMangaData> = emptyList())

@Serializable
internal class WebMangaData(
    val topicInfo: WebMangaDetails,
    val comicList: List<WebMangaChapter> = emptyList(),
)

@Serializable
internal class WebMangaDetails(
    val title: String,
    @SerialName("vertical_image_url") val verticalImageUrl: String,
    val user: WebAuthor,
    val description: String,
    @SerialName("update_status") val updateStatus: String,
)

@Serializable
internal class WebAuthor(val nickname: String)

@Serializable
internal class WebMangaChapter(
    val id: Int,
    val title: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
internal class WebChapterPayload(val data: List<WebChapterData> = emptyList())

@Serializable
internal class WebChapterData(val res: WebChapterResponse)

@Serializable
internal class WebChapterResponse(val data: WebChapterResponseData)

@Serializable
internal class WebChapterResponseData(
    @SerialName("comic_info") val comicInfo: WebChapter,
)

@Serializable
internal class WebChapter(
    @SerialName("comic_images") val comicImages: List<WebPage> = emptyList(),
)

@Serializable
internal class WebPage(
    private val url: String,
    private val url1280: String? = null,
) {
    val imageUrl get() = url1280 ?: url
}

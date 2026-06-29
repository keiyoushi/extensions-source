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
internal data class ApiMangaResponse(val data: ApiMangaData)

@Serializable
internal data class ApiMangaData(
    val title: String,
    @SerialName("vertical_image_url") val verticalImageUrl: String,
    val user: ApiUser,
    val description: String,
    @SerialName("update_status_code") val updateStatusCode: Int,
    val comics: List<ApiChapter> = emptyList(),
)

@Serializable
internal data class ApiUser(val nickname: String)

@Serializable
internal data class ApiChapter(
    val id: Int,
    val title: String,
    @SerialName("can_view") val canView: Boolean = false,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
internal data class WebChapterPayload(val data: List<WebChapterData> = emptyList())

@Serializable
internal data class WebChapterData(val comicInfo: WebChapter)

@Serializable
internal data class WebChapter(val comicImages: List<WebPage> = emptyList())

@Serializable
internal data class WebPage(val url: String)

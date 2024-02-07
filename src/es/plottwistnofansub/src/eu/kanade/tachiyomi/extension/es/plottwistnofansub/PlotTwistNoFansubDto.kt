package eu.kanade.tachiyomi.extension.es.plottwistnofansub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResultDto(
    @SerialName("td_data") val data: String,
)

@Serializable
data class ChapterDto(
    @SerialName("chapter_name") val name: String,
    @SerialName("chapter_number") val number: String,
    @SerialName("post_name") val mangaSlug: String,
)

@Serializable
data class PagesPayloadDto(
    @SerialName("image_url") val cdnUrl: String,
    @SerialName("title") val mangaSlug: String,
    @SerialName("actual") val chapterNumber: String,
    val images: List<ImagesDto>,
)

@Serializable
data class ImagesDto(
    @SerialName("manga_id") val mangaId: String,
    @SerialName("image_name") val imageName: String,
)

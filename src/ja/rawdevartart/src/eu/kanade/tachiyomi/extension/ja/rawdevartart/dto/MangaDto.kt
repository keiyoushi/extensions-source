package eu.kanade.tachiyomi.extension.ja.rawdevartart.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaDto(
    @SerialName("manga_name") val name: String,
    @SerialName("manga_cover_img") val coverImage: String,
    @SerialName("manga_id") val id: Int,
    @SerialName("manga_others_name") val alternativeName: String? = null,
    @SerialName("manga_status") val status: Boolean = false,
    @SerialName("manga_description") val description: String? = null,
    @SerialName("manga_cover_img_full") val coverImageFull: String? = null,
)

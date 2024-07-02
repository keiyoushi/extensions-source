package eu.kanade.tachiyomi.extension.ja.nicovideoseiga.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Manga(
    val id: Int,
    val meta: MangaMetadata,
) {
    @Serializable
    data class MangaMetadata(
        val title: String,
        @SerialName("display_author_name")
        val author: String,
        val description: String,
        @SerialName("serial_status")
        val serialStatus: String,
        @SerialName("square_image_url")
        val thumbnailUrl: String,
        @SerialName("share_url")
        val shareUrl: String,
    )
}

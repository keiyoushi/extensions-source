package eu.kanade.tachiyomi.extension.ja.nicovideoseiga.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Chapters are known as Episodes internally in the API
@Serializable
data class Chapter(
    val id: Int,
    val meta: ChapterMetadata,
    @SerialName("own_status")
    val ownership: Ownership,
) {
    @Serializable
    data class ChapterMetadata(
        val title: String,
        val number: Int,
        @SerialName("created_at")
        val createdAt: Long,
    )

    @Serializable
    data class Ownership(
        @SerialName("sell_status")
        val sellStatus: String,
    )
}

package eu.kanade.tachiyomi.extension.ja.nicovideoseiga.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Chapter(
    val id: Int,
    val meta: ChapterMetadata
) {
    @Serializable
    data class ChapterMetadata (
        val title: String,
        val number: Int,
        @SerialName("created_at")
        val createdAt: Long,
        @SerialName("own_status")
        val ownership: Ownership
    ) {
        @Serializable
        data class Ownership (
            @SerialName("sell_status")
            val sellStatus: String
        )
    }
}

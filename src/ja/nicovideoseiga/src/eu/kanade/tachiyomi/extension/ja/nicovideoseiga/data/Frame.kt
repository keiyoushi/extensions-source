package eu.kanade.tachiyomi.extension.ja.nicovideoseiga.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Frame(
    val id: Int,
    val meta: FrameMetadata
) {
    @Serializable
    data class FrameMetadata(
        @SerialName("source_url")
        val sourceUrl: String
    )
}

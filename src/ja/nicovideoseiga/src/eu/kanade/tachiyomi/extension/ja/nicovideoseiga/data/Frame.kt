package eu.kanade.tachiyomi.extension.ja.nicovideoseiga.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Frames are the internal name for pages in the API
@Serializable
data class Frame(
    val id: Int,
    val meta: FrameMetadata,
) {
    @Serializable
    data class FrameMetadata(
        @SerialName("source_url")
        val sourceUrl: String,
    )
}

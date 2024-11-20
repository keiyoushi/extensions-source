package eu.kanade.tachiyomi.extension.en.snowmtl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PageDto(
    @SerialName("img_url")
    val imageUrl: String,
    val translations: List<List<String>>,
)

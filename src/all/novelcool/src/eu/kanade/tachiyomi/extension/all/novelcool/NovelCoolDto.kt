package eu.kanade.tachiyomi.extension.all.novelcool

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NovelCoolBrowsePayload(
    val appId: String,
    @SerialName("keyword") val query: String? = null,
    val lang: String,
    @SerialName("lc_type") val type: String,
    val page: String,
    @SerialName("page_size") val size: String,
    val secret: String,
)

@Serializable
data class NovelCoolBrowseResponse(
    val list: List<Manga>? = emptyList(),
)

@Serializable
data class Manga(
    val url: String,
    val name: String,
    val cover: String,
)

package eu.kanade.tachiyomi.extension.th.reborntrans.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WpPost(
    val id: Int,
    val link: String,
    val title: WpRendered,
    val excerpt: WpRendered,
    val content: WpRendered,
    val meta: WpMeta? = null,
    @SerialName("class_list") val classList: List<String> = emptyList(),
    @SerialName("_embedded") val embedded: WpEmbedded? = null,
)

@Serializable
data class WpRendered(
    val rendered: String,
)

@Serializable
data class WpMeta(
    @SerialName("_manga_eagle_manga_cover_url") val coverUrl: String? = null,
    @SerialName("_manga_eagle_manga_work_status") val workStatus: String? = null,
)

@Serializable
data class WpEmbedded(
    @SerialName("wp:featuredmedia") val featuredMedia: List<WpMedia>? = null,
    @SerialName("wp:term") val terms: List<List<WpTerm>>? = null,
)

@Serializable
data class WpMedia(
    @SerialName("source_url") val sourceUrl: String? = null,
)

@Serializable
data class WpTerm(
    val name: String? = null,
    val taxonomy: String? = null,
)

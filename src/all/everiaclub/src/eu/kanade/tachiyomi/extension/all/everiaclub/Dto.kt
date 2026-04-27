package eu.kanade.tachiyomi.extension.all.everiaclub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class WPPostDto(
    val link: String,
    val title: WPRenderedDto,
    @SerialName("_embedded") private val embedded: WPEmbeddedDto? = null,
) {
    val thumbnail: String?
        get() = embedded?.featuredMedia?.getOrNull(0)?.sourceUrl
}

@Serializable
class WPRenderedDto(
    val rendered: String,
)

@Serializable
class WPEmbeddedDto(
    @SerialName("wp:featuredmedia") val featuredMedia: List<WPFeaturedMediaDto>? = null,
)

@Serializable
class WPFeaturedMediaDto(
    @SerialName("source_url") val sourceUrl: String,
)

@Serializable
class WPCategoryDto(
    val id: Int,
    val name: String,
)

@Serializable
class WPTagDto(
    val id: Int,
    val name: String,
)

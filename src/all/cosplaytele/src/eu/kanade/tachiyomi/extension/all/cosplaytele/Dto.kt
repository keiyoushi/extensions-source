package eu.kanade.tachiyomi.extension.all.cosplaytele

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PopularPostDto(
    val title: RenderedStringDto,
    val link: String,
    @SerialName("_embedded") val embedded: EmbeddedDto? = null,
)

@Serializable
class RenderedStringDto(val rendered: String)

@Serializable
class EmbeddedDto(
    @SerialName("wp:featuredmedia") val featuredMedia: List<FeaturedMediaDto>? = null,
)

@Serializable
class FeaturedMediaDto(
    @SerialName("source_url") val sourceUrl: String,
)

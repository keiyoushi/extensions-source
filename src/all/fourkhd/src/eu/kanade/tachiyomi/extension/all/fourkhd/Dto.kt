package eu.kanade.tachiyomi.extension.all.fourkhd

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PostDto(
    val id: Int = 0,
    val date: String = "",
    val link: String = "",
    val title: RenderedStringDto = RenderedStringDto(),
    val content: RenderedStringDto = RenderedStringDto(),
    @SerialName("jetpack_featured_media_url") val jetpackFeaturedMediaUrl: String? = null,
    @SerialName("_embedded") val embedded: EmbeddedDto? = null,
)

@Serializable
class RenderedStringDto(
    val rendered: String = "",
)

@Serializable
class EmbeddedDto(
    @SerialName("wp:featuredmedia") val featuredMedia: List<FeaturedMediaDto>? = null,
    @SerialName("wp:term") val terms: List<List<TermDto>>? = null,
)

@Serializable
class FeaturedMediaDto(
    @SerialName("source_url") val sourceUrl: String = "",
)

@Serializable
class TermDto(
    val name: String = "",
)

package eu.kanade.tachiyomi.extension.en.futapo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class EntryDto(
    val link: String,
    val title: RenderedDto,
    @SerialName("_embedded") private val embedded: EmbeddedDto? = null,
) {
    val cover get() = embedded?.cover
}

@Serializable
class SearchResultDto(
    val id: Int,
    val title: String,
    val url: String,
    val subtype: String,
)

@Serializable
class CoverDto(
    val id: Int,
    @SerialName("_embedded") private val embedded: EmbeddedDto? = null,
) {
    val cover get() = embedded?.cover
}

@Serializable
class DetailsDto(
    @SerialName("date_gmt") val dateGmt: String,
    val title: RenderedDto,
    @SerialName("_embedded") private val embedded: EmbeddedDto? = null,
) {
    val cover get() = embedded?.cover
}

@Serializable
class RenderedDto(val rendered: String)

@Serializable
class EmbeddedDto(
    @SerialName("wp:featuredmedia") private val featuredMedia: List<MediaDto>? = null,
) {
    val cover get() = featuredMedia?.firstOrNull()?.let { it.sizes?.large?.sourceUrl ?: it.sourceUrl }
}

@Serializable
class MediaDto(
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("media_details") private val details: MediaDetailsDto? = null,
) {
    val sizes get() = details?.sizes
}

@Serializable
class MediaDetailsDto(val sizes: MediaSizesDto? = null)

@Serializable
class MediaSizesDto(val large: MediaSizeDto? = null)

@Serializable
class MediaSizeDto(@SerialName("source_url") val sourceUrl: String)

@Serializable
class TagDto(val id: Int, val name: String)

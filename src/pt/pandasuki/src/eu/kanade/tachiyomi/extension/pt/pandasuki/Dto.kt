package eu.kanade.tachiyomi.extension.pt.pandasuki

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.parser.Parser

@Serializable
class MangaDto(
    private val slug: String,
    private val title: RenderedDto,
    @SerialName("_embedded") private val embedded: EmbeddedDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = Parser.unescapeEntities(this@MangaDto.title.rendered, false)
        thumbnail_url = embedded?.featuredMedia?.firstOrNull()?.sourceUrl
    }
}

@Serializable
class RenderedDto(val rendered: String)

@Serializable
class EmbeddedDto(@SerialName("wp:featuredmedia") val featuredMedia: List<MediaDto>? = null)

@Serializable
class MediaDto(@SerialName("source_url") val sourceUrl: String? = null)

@Serializable
class GenreDto(
    val id: Int,
    val name: String,
)

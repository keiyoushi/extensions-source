package eu.kanade.tachiyomi.extension.all.ithinkilikeyou

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.parser.Parser

@Serializable
class SeriesDto(
    val id: Int,
    val slug: String,
    private val title: RenderedDto,
    private val content: RenderedDto? = null,
    @SerialName("_embedded") private val embedded: EmbeddedDto? = null,
) {
    val name get() = Parser.unescapeEntities(title.rendered, false)

    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "/series/$slug/"
        title = name
        thumbnail_url = embedded?.featuredMedia?.firstOrNull()?.sourceUrl
        genre = embedded?.terms?.flatten()?.joinToString { Parser.unescapeEntities(it.name, false) }?.ifEmpty { null }
        // Series for patrons only show that instead of a description
        description = content?.rendered?.takeUnless { "patron_only" in it }
            ?.let { MarkdownDescription.Builder().description(it.asJsoup(baseUrl).body()).build() }
    }
}

@Serializable
class EmbeddedDto(
    @SerialName("wp:featuredmedia") val featuredMedia: List<MediaDto> = emptyList(),
    @SerialName("wp:term") val terms: List<List<TermDto>> = emptyList(),
)

@Serializable
class MediaDto(@SerialName("source_url") val sourceUrl: String)

@Serializable
class TermDto(val name: String)

@Serializable
class RenderedDto(val rendered: String)

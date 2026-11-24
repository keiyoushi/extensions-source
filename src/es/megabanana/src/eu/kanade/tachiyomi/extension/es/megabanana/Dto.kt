package eu.kanade.tachiyomi.extension.es.megabanana

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.toMarkdown
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

@Serializable
class CatalogDto(
    val items: List<CatalogItemDto>,
    val pages: Int,
)

@Serializable
class CatalogItemDto(
    private val id: Int,
    private val title: String,
    private val thumbnail: String? = null,
    val readerReady: Boolean,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@CatalogItemDto.title
        thumbnail_url = thumbnail
    }
}

@Serializable
class PostDto(
    val id: Int,
    private val title: RenderedDto,
    private val content: RenderedDto,
    @SerialName("_embedded") private val embedded: EmbeddedDto,
) {
    fun toSManga(baseUrl: String, reader: ReaderDto) = SManga.create().apply {
        url = id.toString()
        title = Parser.unescapeEntities(this@PostDto.title.rendered, false)
        description = Jsoup.parseBodyFragment(content.rendered, baseUrl).select("p, .wp-block-embed blockquote")
            .map { it.toMarkdown() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .ifEmpty { null }
        genre = embedded.terms.flatten().map { Parser.unescapeEntities(it.name, false) }.distinct().joinToString().ifEmpty { null }
        thumbnail_url = embedded.featuredMedia?.firstOrNull()?.sourceUrl
        // The site marks a finished comic's last chapter "(Fin)"
        if (reader.chapters.lastOrNull()?.title?.lowercase()?.contains("(fin)") == true) status = SManga.COMPLETED
    }
}

@Serializable
class RenderedDto(val rendered: String)

@Serializable
class EmbeddedDto(
    @SerialName("wp:featuredmedia") val featuredMedia: List<MediaDto>? = null,
    @SerialName("wp:term") val terms: List<List<TermDto>> = emptyList(),
)

@Serializable
class MediaDto(@SerialName("source_url") val sourceUrl: String? = null)

@Serializable
class TermDto(val name: String)

@Serializable
class ReaderDto(
    val currentChapter: Int,
    val chapters: List<ReaderChapterDto>,
    val pages: List<String>,
)

@Serializable
class ReaderChapterDto(
    private val number: String,
    val title: String,
) {
    fun toSChapter(postId: String) = SChapter.create().apply {
        url = "$postId/$number"
        name = title
    }
}

@Serializable
class CategoryDto(
    val id: Int,
    val name: String,
    val slug: String,
    val parent: Int,
)

@Serializable
class NavigationDto(val items: List<CategoryDto>)

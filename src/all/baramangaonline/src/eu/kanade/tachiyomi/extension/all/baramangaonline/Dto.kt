package eu.kanade.tachiyomi.extension.all.baramangaonline

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.parser.Parser
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class PostDto(
    private val id: Int,
    private val title: RenderedDto,
    val categories: List<Int>,
    @SerialName("yoast_head_json") private val yoastHead: YoastHeadDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@PostDto.title.text()
        thumbnail_url = yoastHead?.cover()
    }
}

@Serializable
class PostDetailsDto(
    private val id: Int,
    private val title: RenderedDto,
    @SerialName("date_gmt") private val dateGmt: String,
    @SerialName("yoast_head_json") private val yoastHead: YoastHeadDto? = null,
    @SerialName("_embedded") private val embedded: EmbeddedDto,
) {
    fun toSManga() = SManga.create().apply {
        // The site's categories are languages, a few content types, and the artists, circles and publishers
        val (languages, others) = embedded.terms.flatten()
            .filter { it.taxonomy == "category" }
            .partition { "/category/languages/" in it.link }
        val (types, creators) = others.partition { term -> TYPES.any { it.second == term.id } }
        url = id.toString()
        title = this@PostDetailsDto.title.text()
        author = creators.joinToString { it.name() }.ifEmpty { null }
        genre = types.joinToString { it.name() }.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .field("Language", languages.map { it.name() })
            .build()
        thumbnail_url = yoastHead?.cover()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toSChapter() = SChapter.create().apply {
        url = id.toString()
        name = "Chapter"
        date_upload = DateTimeFormatter.ISO_LOCAL_DATE_TIME.tryParseDateTime(dateGmt, ZoneOffset.UTC)
    }
}

@Serializable
class PostContentDto(val content: RenderedDto)

@Serializable
class RenderedDto(val rendered: String) {
    fun text(): String = Parser.unescapeEntities(rendered, false)
}

@Serializable
class YoastHeadDto(@SerialName("og_image") private val ogImage: List<OgImageDto> = emptyList()) {
    // The post's featured image, which is its first page, at its full size (sometimes given in http)
    fun cover() = ogImage.firstOrNull()?.url?.replaceFirst("http://", "https://")
}

@Serializable
class OgImageDto(val url: String)

@Serializable
class EmbeddedDto(@SerialName("wp:term") val terms: List<List<TermDto>>)

@Serializable
class TermDto(
    val id: Int,
    private val name: String,
    val link: String,
    val taxonomy: String,
) {
    fun name(): String = Parser.unescapeEntities(name, false)
}

@Serializable
class CategoryDto(val id: Int)

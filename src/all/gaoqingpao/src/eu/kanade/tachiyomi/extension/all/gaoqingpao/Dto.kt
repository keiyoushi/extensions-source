package eu.kanade.tachiyomi.extension.all.gaoqingpao

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

@Serializable
class PostDto(
    private val id: Int,
    private val title: RenderedDto,
    private val content: RenderedDto,
    @SerialName("date_gmt") val dateGmt: String?,
    @SerialName("_embedded") private val embedded: EmbeddedDto?,
) {
    fun toSManga(imageBaseUrl: String) = SManga.create().apply {
        url = id.toString()
        title = Parser.unescapeEntities(this@PostDto.title.rendered, false)
        thumbnail_url = content.imageUrls(imageBaseUrl).firstOrNull()
        genre = embedded?.terms?.flatten()?.joinToString { it.name }
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }
}

@Serializable
class PostContentDto(
    val content: RenderedDto,
)

@Serializable
class RenderedDto(
    val rendered: String,
) {
    fun imageUrls(imageBaseUrl: String): List<String> = Jsoup.parseBodyFragment(rendered, imageBaseUrl)
        .select("img[src]")
        .map { it.absUrl("src") }
}

@Serializable
class EmbeddedDto(
    @SerialName("wp:term") val terms: List<List<TermDto>>,
)

@Serializable
class TermDto(
    val id: Int,
    val name: String,
    val slug: String,
)

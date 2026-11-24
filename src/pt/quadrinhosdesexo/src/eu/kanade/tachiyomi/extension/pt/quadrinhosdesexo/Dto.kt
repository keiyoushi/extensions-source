package eu.kanade.tachiyomi.extension.pt.quadrinhosdesexo

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.parser.Parser

@Serializable
class PostDto(
    private val slug: String,
    val link: String,
    private val title: Rendered,
    @SerialName("yoast_head_json") private val yoast: YoastDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = Parser.unescapeEntities(this@PostDto.title.rendered, false)
        thumbnail_url = yoast?.ogImage?.firstOrNull()?.url
    }
}

@Serializable
class Rendered(val rendered: String)

@Serializable
class YoastDto(@SerialName("og_image") val ogImage: List<ImageDto>? = null)

@Serializable
class ImageDto(val url: String)

@Serializable
class CategoryDto(
    val id: Int,
    val name: String,
    val count: Int,
)

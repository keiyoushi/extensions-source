package eu.kanade.tachiyomi.extension.all.fsicomics

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.parser.Parser

@Serializable
class WPPostDto(
    val link: String,
    val title: WPRenderedDto,
    val categories: List<Int> = emptyList(),
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
    val slug: String = "",
    val parent: Int = 0,
)

@Serializable
class WPTagDto(
    val id: Int,
    val name: String,
    val slug: String = "",
)

fun WPPostDto.toSManga(): SManga {
    val renderedTitle = Parser.unescapeEntities(title.rendered, false)
    return SManga.create().apply {
        title = renderedTitle
        url = link.toHttpUrl().encodedPath
        thumbnail_url = thumbnail
    }
}

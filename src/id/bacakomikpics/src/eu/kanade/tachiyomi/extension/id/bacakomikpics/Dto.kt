package eu.kanade.tachiyomi.extension.id.bacakomikpics

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.parser.Parser

@Serializable
class SeriesDto(
    private val link: String,
    private val title: RenderedDto,
    @SerialName("_embedded") private val embedded: EmbeddedDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = link.toHttpUrl().encodedPath
        title = Parser.unescapeEntities(this@SeriesDto.title.rendered, false)
        thumbnail_url = embedded?.media?.firstOrNull()?.sourceUrl
    }
}

@Serializable
class RenderedDto(val rendered: String)

@Serializable
class EmbeddedDto(
    @SerialName("wp:featuredmedia") val media: List<MediaDto>? = null,
)

@Serializable
class MediaDto(
    @SerialName("source_url") val sourceUrl: String? = null,
)

@Serializable
class GenreDto(
    val id: Int,
    val name: String,
)

@Serializable
class ChapterDto(
    val title: String,
    val url: String,
)

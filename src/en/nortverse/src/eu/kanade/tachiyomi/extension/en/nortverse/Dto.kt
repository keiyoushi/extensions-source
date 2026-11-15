package eu.kanade.tachiyomi.extension.en.nortverse

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.parser.Parser
import kotlin.time.Instant

@Serializable
class ComicDto(
    private val slug: String,
    private val title: RenderedDto,
    @SerialName("date_gmt") private val dateGmt: String,
) {
    fun toSChapter(number: Int) = SChapter.create().apply {
        url = slug
        name = "#$number. ${Parser.unescapeEntities(title.rendered, false)}"
        date_upload = Instant.tryParse("${dateGmt}Z")
    }
}

@Serializable
class ContentDto(
    val content: RenderedDto,
)

@Serializable
class RenderedDto(
    val rendered: String,
)

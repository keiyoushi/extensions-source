package eu.kanade.tachiyomi.extension.en.theinterpretationofshadows

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.parser.Parser
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class ComicDto(
    @SerialName("date_gmt") private val dateGmt: String,
    private val link: String,
    private val title: RenderedDto,
) {
    fun toSChapter(number: Int) = SChapter.create().apply {
        url = link.toHttpUrl().encodedPath
        name = Parser.unescapeEntities(title.rendered, false)
        chapter_number = number.toFloat()
        date_upload = DateTimeFormatter.ISO_LOCAL_DATE_TIME.tryParseDateTime(dateGmt, ZoneOffset.UTC)
    }
}

@Serializable
class RenderedDto(
    val rendered: String,
)

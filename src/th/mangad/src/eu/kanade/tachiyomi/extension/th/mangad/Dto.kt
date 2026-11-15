package eu.kanade.tachiyomi.extension.th.mangad

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.parser.Parser
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class CategoryDto(
    val id: Int,
    val name: String,
    val description: String,
)

@Serializable
class PostDto(
    @SerialName("date_gmt") private val dateGmt: String,
    private val title: RenderedDto,
    private val link: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = link.toHttpUrl().encodedPath
        name = Parser.unescapeEntities(title.rendered, false)
        date_upload = DATE_FORMAT.tryParseDateTime(dateGmt, ZoneOffset.UTC)
    }
}

@Serializable
class PostLinkDto(
    private val link: String,
) {
    // A chapter's path is /<list>/<series>/<chapter>/, its series' /category/<list>/<series>/
    fun seriesUrl(list: String) = "/category/$list/${link.toHttpUrl().pathSegments[1]}/"
}

@Serializable
class RenderedDto(
    val rendered: String,
)

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME

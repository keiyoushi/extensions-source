package eu.kanade.tachiyomi.extension.all.jagodibuja

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.parser.Parser
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME

@Serializable
class Post(
    @SerialName("date_gmt") private val dateGmt: String,
    val modified: String,
    private val link: String,
    private val title: Rendered,
    val content: Rendered? = null,
) {
    fun toSChapter(name: String = Parser.unescapeEntities(title.rendered, false)) = SChapter.create().apply {
        url = link.toHttpUrl().encodedPath
        this.name = name
        date_upload = dateFormat.tryParseDateTime(dateGmt, ZoneOffset.UTC)
    }
}

@Serializable
class Rendered(
    val rendered: String,
)

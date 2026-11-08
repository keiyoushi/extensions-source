package eu.kanade.tachiyomi.extension.ar.comicsgate

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.toMarkdown
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
class Post(
    private val link: String,
    private val title: Rendered,
    private val content: Rendered,
    @SerialName("date_gmt") private val date: String,
) {
    private fun body() = Jsoup.parseBodyFragment(content.rendered, link).body()

    // Folder of the reader the post embeds in an iframe
    private fun Element.readerPath() = selectFirst("iframe[src*=/bonus/comics/]")?.absUrl("src")
        ?.toHttpUrl()?.encodedPath?.substringBeforeLast('/')?.plus("/")

    fun toSManga(baseUrl: String) = SManga.create().apply {
        val body = body()
        url = link.toHttpUrl().encodedPath
        title = Parser.unescapeEntities(this@Post.title.rendered, false)
        thumbnail_url = body.readerPath()?.let { "$baseUrl${it}l/1.jpg" }
        description = MarkdownDescription.Builder()
            .markdown(
                body.children()
                    .filter { it.selectFirst("iframe, img, script, a[href*=/phpbbx/]") == null && !it.hasClass("hupso-share-buttons") }
                    .joinToString("\n\n") { it.toMarkdown() },
            )
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toSChapter() = body().readerPath()?.let { path ->
        SChapter.create().apply {
            url = path
            name = "Chapter"
            date_upload = DATE_FORMAT.tryParseDateTime(date, ZoneOffset.UTC)
        }
    }
}

@Serializable
class Rendered(val rendered: String)

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME

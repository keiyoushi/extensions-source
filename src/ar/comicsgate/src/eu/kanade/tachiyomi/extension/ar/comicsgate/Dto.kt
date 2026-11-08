package eu.kanade.tachiyomi.extension.ar.comicsgate

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.toMarkdown
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import kotlin.time.Instant

@Serializable
class Post(
    private val link: String,
    private val title: Rendered,
    private val content: Rendered,
    @SerialName("date_gmt") private val dateGmt: String,
) {
    private fun body() = Jsoup.parseBodyFragment(content.rendered, link).body()

    // Folder of the reader the post embeds in an iframe
    private fun Element.readerUrl(): HttpUrl? = selectFirst("iframe[src*=/bonus/comics/]")?.absUrl("src")
        ?.toHttpUrl()?.resolve("./")

    fun toSManga() = SManga.create().apply {
        val body = body()
        url = link.toHttpUrl().encodedPath
        title = Parser.unescapeEntities(this@Post.title.rendered, false)
        thumbnail_url = body.readerUrl()?.resolve("m/1.jpg")?.toString()
        description = MarkdownDescription.Builder()
            .markdown(
                body.children()
                    .filter { it.selectFirst("iframe, img, script, a[href*=/phpbbx/]") == null && !it.hasClass("hupso-share-buttons") }
                    .joinToString("\n\n") { it.toMarkdown() },
            )
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    fun toSChapter() = SChapter.create().apply {
        url = body().readerUrl()!!.encodedPath
        name = "Chapter"
        date_upload = Instant.tryParse("${dateGmt}Z")
    }
}

@Serializable
class Rendered(val rendered: String)

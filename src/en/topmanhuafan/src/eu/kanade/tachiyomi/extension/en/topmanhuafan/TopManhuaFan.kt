package eu.kanade.tachiyomi.extension.en.topmanhuafan

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import okhttp3.Request
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TopManhuaFan : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH)
    override val mangaSubString = "manhua"
    override val imageFetch = Fetch.IMAGE

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        description = description?.split("\n\n")
            ?.filterNot { it.isBlank() || it == title || it == "${intl["alt_names_heading"]} $title" }
            ?.joinToString("\n\n")
            ?.removePrefix("Read manga $title")
            ?.trim()
            ?.ifEmpty { null }
    }

    override fun chapterListSelector() = "div.wp-manga-chapter"

    // Image hosts refuse the theme's chapter Referer, and images sent with Origin miss the Cloudflare cache
    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .fetch(imageFetch.from(page.url))
        .build()
}

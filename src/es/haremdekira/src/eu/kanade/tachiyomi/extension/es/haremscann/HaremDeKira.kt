package eu.kanade.tachiyomi.extension.es.haremdekira

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HaremDeKira : MadaraNoAjax() {
    override val supportsPostId = false
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

    override val mangaSubString = "serie"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds) { baseUrl.toHttpUrl().host == it.host }

    override fun archiveManga(element: Element, id: String) = super.archiveManga(element, id)?.apply {
        thumbnail_url = thumbnail_url ?: element.selectFirst("[style*='background-image']")
            ?.attr("style")
            ?.substringAfter("url(")
            ?.substringBefore(")")
    }
    override fun archiveSelector() = "button.group"
    override val archiveUrlSelector = "a"
    override val archiveTitleSelector = "h3"

    override val mangaDetailsSelectorTitle = "div.wp-manga div.grid > h1"
    override val mangaDetailsSelectorStatus = "div.wp-manga div[alt=type]:eq(0) > span"
    override val mangaDetailsSelectorGenre = "div.wp-manga div[alt=type]:gt(0) > span"
    override val mangaDetailsSelectorDescription = "div.wp-manga div#expand_content"

    override fun chapterListSelector() = "ul#list-chapters li > a"
    override val chapterNameSelector = "div.grid > span"
    override val chapterDateSelector = "div.grid > div"
    override val chapterUrlSelector = "a"
}

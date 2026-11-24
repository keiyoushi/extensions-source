package eu.kanade.tachiyomi.extension.fr.mangahubfr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaHubFr : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.vip-permission)"

    override fun imageFromElement(element: Element): String? = super.imageFromElement(element)?.trim()

    // The one-time image tokens are tied to the PHP session. A session created by an AJAX call (popular, search)
    // gets empty image responses, so start the chapter page from a fresh session created by this HTML request.
    override suspend fun fetchChapterDocument(chapterUrl: String): Document {
        val url = baseUrl.toHttpUrl()
        val expired = Cookie.Builder().name("PHPSESSID").value("").hostOnlyDomain(url.host).path("/").expiresAt(0).build()
        client.cookieJar.saveFromResponse(url, listOf(expired))
        return super.fetchChapterDocument(chapterUrl)
    }
}

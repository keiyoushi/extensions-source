package eu.kanade.tachiyomi.extension.ja.rawinu

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class RawINU : FMReader(
    "RawINU",
    "https://rawinu.com",
    "ja",
) {
    override val client = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    // =========================== Manga Details ============================
    override val infoElementSelector = "div.card-body div.row"

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/manga-").substringBefore(".html")
        return GET("$baseUrl/app/manga/controllers/cont.Listchapter.php?slug=$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        doc.setBaseUri(baseUrl) // Fixes chapter URLs
        return doc.select(chapterListSelector()).map(::chapterFromElement)
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.attr(chapterNameAttrSelector).trim()
        date_upload = element.select(chapterTimeSelector).run { if (hasText()) parseRelativeDate(text()) else 0 }
    }
}

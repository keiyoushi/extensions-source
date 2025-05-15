package eu.kanade.tachiyomi.extension.ja.comictop

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ComicTop : ParsedHttpSource() {

    override val name = "ComicTop"

    override val baseUrl = "https://comic-top.com"

    override val lang = "ja"

    override val supportsLatest = true

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popular/page/$page", headers)

    override fun popularMangaSelector() = ".animposx > a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val img = element.selectFirst("img")!!
        thumbnail_url = (if (element.hasAttr("data-src")) element.absUrl("data-src") else img.absUrl("src")).substringBefore("?")
        title = img.attr("title")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = "#nextpagination"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-chapter/page/$page/", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET(
        "$baseUrl/page/$page".toHttpUrl().newBuilder()
            .addQueryParameter("s", query).build(),
        headers,
    )

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    // ============================== Filters ===============================

    // No filters
}

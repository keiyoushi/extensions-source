package eu.kanade.tachiyomi.extension.ja.comiplex

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element

class Comiplex :
    GigaViewer(
        "Comiplex",
        "https://viewer.heros-web.com",
        "ja",
    ) {
    override val supportsLatest: Boolean = false

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series/heros", headers)

    override val popularMangaSelector: String = "ul.series-items li.series-item > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h4.item-series-title")!!.text()
        thumbnail_url = element.selectFirst("div.series-item-thumb img")?.absUrl("data-src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("ヒーローズ", "heros"),
        Collection("ふらっとヒーローズ", "flat"),
        Collection("わいるどヒーローズ", "wild"),
        Collection("読切作品", "oneshot"),
    )
}

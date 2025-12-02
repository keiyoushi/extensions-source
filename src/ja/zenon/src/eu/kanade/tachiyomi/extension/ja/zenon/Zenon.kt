package eu.kanade.tachiyomi.extension.ja.zenon

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element

class Zenon : GigaViewer(
    "Zenon",
    "https://comic-zenon.com",
    "ja",
    "https://cdn-img.comic-zenon.com/public/page",
    true,
) {

    override val supportsLatest: Boolean = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "ゼノン編集部"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series/zenyon", headers)

    override fun popularMangaSelector(): String = ".series-item"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".item-series-title")!!.text()
        thumbnail_url = element.selectFirst(".img-wrapper img")?.let { img ->
            img.absUrl("data-src").ifEmpty { img.absUrl("src") }
        }
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun searchMangaSelector(): String = "ul.series-list > li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".series-title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("コミックぜにょん", "zenyon"),
        Collection("月刊コミックゼノン", "zenon"),
        Collection("コミックタタン", "tatan"),
        Collection("読切作品", "oneshot"),
        Collection("漫画賞", "newcomer"),
    )
}

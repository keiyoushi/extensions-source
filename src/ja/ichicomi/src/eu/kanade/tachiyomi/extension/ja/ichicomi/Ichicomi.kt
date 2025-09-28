package eu.kanade.tachiyomi.extension.ja.ichicomi

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element

class Ichicomi : GigaViewer(
    "Ichicomi",
    "https://ichicomi.com",
    "ja",
    "https://cdn-img.ichicomi.com",
    isPaginated = true,
) {
    override val publisher: String = "一迅社"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series", headers)

    override fun popularMangaSelector(): String = "div[class^=Series_series__]"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))
        title = link.selectFirst("h4[class^=Series_title__]")!!.text()
        thumbnail_url = link.selectFirst("img[class^=Series_thumbnail__]")?.attr("src")
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector(): String = "li[class^=UpdatedSeriesItem_updated_series_item__]"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))
        title = link.selectFirst("h4[class^=UpdatedSeriesItem_title__]")!!.text()
        thumbnail_url = link.selectFirst("img[class^=UpdatedSeriesItem_thumbnail__]")?.attr("src")
    }

    override fun searchMangaSelector(): String = "li[class^=SearchResultItem_li__]"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))
        title = element.selectFirst("p[class^=SearchResultItem_series_title__]")!!.text()
        thumbnail_url = link.selectFirst("img")?.attr("src")
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("全作品", ""),
        Collection("echo", "echo"),
        Collection("gateau", "gateau"),
        Collection("カラフルハピネス", "colorful_happiness"),
        Collection("REX", "rex"),
        Collection("HOWL", "howl"),
        Collection("POOL", "pool"),
        Collection("百合姫", "yurihime"),
        Collection("LAKE", "lake"),
        Collection("ZERO-SUM", "zerosum"),
        Collection("ぱれっと", "palette"),
        Collection("ベビードール", "babydoll"),
        Collection("一迅プラス", "ichijin-plus"),
    )
}

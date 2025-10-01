package eu.kanade.tachiyomi.extension.ja.magcomi

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

class MagComi : GigaViewer(
    "MAGCOMI",
    "https://magcomi.com",
    "ja",
    "https://cdn-img.magcomi.com/public/page",
    isPaginated = true,
) {

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "マッグガーデン"

    override fun popularMangaSelector(): String = "ul[class^=\"SeriesSection_series_list\"] > li > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h3").text()
        thumbnail_url = element.select("div.jsx-series-thumb > span > noscript > img").attr("src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun searchMangaSelector(): String = "li[class^=SearchResultItem_li__]"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))
        title = element.selectFirst("p[class^=SearchResultItem_series_title__]")!!.text()
        thumbnail_url = link.selectFirst("img")?.attr("src")
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載中", ""),
        Collection("読切", "oneshot"),
        Collection("漫画賞・他", "award_other"),
        Collection("完結・休止", "finished"),
    )
}

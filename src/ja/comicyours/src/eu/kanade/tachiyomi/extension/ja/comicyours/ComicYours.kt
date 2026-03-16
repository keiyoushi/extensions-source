package eu.kanade.tachiyomi.extension.ja.comicyours

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element

class ComicYours :
    GigaViewer(
        "Comic Y-OURs",
        "https://comic-y-ours.com",
        "ja",
    ) {
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override val popularMangaSelector: String = "a[class^=MainVisual_imageLink_], li[class^=SeriesPageItem_item_] a[class^=SeriesPageItem_itemLink_]"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.attr("data-series-name")
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override val latestUpdatesSelector: String = "ul[class^=LatestUpdatedSeries_LatestUpdatedSeriesList_] li"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a[class^=SeriesItem_seriesItemLink_]")!!
        title = link.attr("data-series-name")
        thumbnail_url = link.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(link.absUrl("href"))
    }

    override val searchMangaSelector: String = "li[class^=SearchResultItem_li_]"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("p[class^=SearchResultItem_series_title_]")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("すべて", ""),
        Collection("読切作品", "oneshot"),
    )
}

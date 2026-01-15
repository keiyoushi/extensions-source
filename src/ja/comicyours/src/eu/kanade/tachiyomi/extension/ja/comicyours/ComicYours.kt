package eu.kanade.tachiyomi.extension.ja.comicyours

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class ComicYours : GigaViewer(
    "Comic Y-OURs",
    "https://comic-y-ours.com",
    "ja",
    "https://cdn-img.comic-y-ours.com/public/page",
    true,
) {
    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher = "少年画報社"

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "a[class^=MainVisual_imageLink_], li[class^=SeriesPageItem_item_] a[class^=SeriesPageItem_itemLink_]"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.attr("data-series-name")
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesSelector(): String = "ul[class^=LatestUpdatedSeries_LatestUpdatedSeriesList_] li"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a[class^=SeriesItem_seriesItemLink_]")!!
        title = link.attr("data-series-name")
        thumbnail_url = link.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(link.absUrl("href"))
    }

    override fun searchMangaSelector(): String = "li[class^=SearchResultItem_li_]"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("p[class^=SearchResultItem_series_title_]")!!.text()
        thumbnail_url = element.selectFirst("img")!!.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val referer = response.request.url.toString()
        val aggregateId = document.selectFirst("script.js-valve")!!.attr("data-giga_series")
        val chapters = mutableListOf<SChapter>()

        var offset = 0

        while (true) {
            val result = paginatedChaptersRequest(referer, aggregateId, offset)
            val resultData = result.parseAs<List<Dto>>()

            if (resultData.isEmpty()) break

            resultData.mapTo(chapters) {
                it.toSChapter(publisher)
            }
            offset += resultData.size
        }
        return chapters
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("すべて", ""),
        Collection("読切作品", "oneshot"),
    )
}

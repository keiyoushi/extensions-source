package eu.kanade.tachiyomi.extension.ja.zenon

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
            img.attr("data-src").ifEmpty { img.attr("src") }
        }
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return super.searchMangaRequest(page, query, filters)
        }

        val collectionFilter = filters.filterIsInstance<CollectionFilter>().firstOrNull()
        val collection = collectionFilter?.toUriPart() ?: "zenyon"

        return GET("$baseUrl/series/$collection", headers)
    }

    override fun searchMangaSelector(): String = "ul.series-list > li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".series-title")!!.text()
        thumbnail_url = element.selectFirst("img")?.attr("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("search")) {
            return super.searchMangaParse(response)
        }

        return popularMangaParse(response)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("検索時はコレクション間を横断して検索します"),
        CollectionFilter(),
    )

    private class CollectionFilter : Filter.Select<String>(
        "コレクション",
        override fun getCollections(): List<Collection> = listOf(
        Collection("コミックぜにょん", "zenyon"),
        Collection("月刊コミックゼノン", "zenon"),
        Collection("コミックタタン", "tatan"),
        Collection("読切作品", "oneshot"),
        Collection("漫画賞", "newcomer"),
    )
}

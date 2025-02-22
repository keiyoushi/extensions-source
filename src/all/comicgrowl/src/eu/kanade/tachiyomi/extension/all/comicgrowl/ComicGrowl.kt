package eu.kanade.tachiyomi.extension.all.comicgrowl

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element

// TODO: get manga status
// TODO: filter by status
// TODO: change cdnUrl as a array(upstream)
class ComicGrowl : GigaViewer(
    "コミックグロウル",
    "https://comic-growl.com",
    "all",
    "https://cdn-img.comic-growl.com/public/page",
) {

    override val publisher = "BUSHIROAD WORKS"

    override val chapterListMode = CHAPTER_LIST_LOCKED

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient =
        super.client.newBuilder().addInterceptor(::imageIntercept).build()

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    // Show only ongoing works
    override fun popularMangaSelector(): String = "ul[class=\"lineup-list ongoing\"] > li > div > a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.select("h5").text()
        thumbnail_url = element.select("div > img").attr("data-src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun latestUpdatesSelector() =
        "div[class=\"update latest\"] > div.card-board > " + "div[class~=card]:not([class~=ad]) > div > a"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        title = element.select("div.data h3").text()
        thumbnail_url = element.select("div.thumb-container img").attr("data-src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載作品", ""),
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder().addQueryParameter("q", query)

            return GET(url.build(), headers)
        }
        return GET(baseUrl, headers) // Currently just get all ongoing works
    }
}

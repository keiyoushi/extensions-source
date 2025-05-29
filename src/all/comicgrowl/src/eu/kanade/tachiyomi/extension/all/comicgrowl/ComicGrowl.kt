package eu.kanade.tachiyomi.extension.all.comicgrowl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ComicGrowl(
    override val lang: String = "all",
    override val baseUrl: String = "https://comic-growl.com",
    override val name: String = "コミックグロウル",
    override val supportsLatest: Boolean = false,
) : ParsedHttpSource() {

    private fun getImageFromElement(element: Element): String {
        val imageUrlRegex = Regex("^.*?webp")
        val match = imageUrlRegex.find(element.selectFirst("source")!!.attr("data-srcset"))
        return "https:" + match!!.value
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/ranking/manga")

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaSelector() = ".ranking-item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create().apply {
            url = element.selectFirst("a")!!.attr("href")
            title = element.selectFirst(".title-text")!!.text()
            author = element.selectFirst(".author-link")!!.text()
            thumbnail_url = getImageFromElement(element)
        }
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        TODO("Not yet implemented")
    }

    override fun chapterListSelector(): String {
        TODO("Not yet implemented")
    }

    override fun imageUrlParse(document: Document): String {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesNextPageSelector(): String? {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesSelector(): String {
        TODO("Not yet implemented")
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst(".series-h-info")!!
        val updateDateElement = infoElement.selectFirst("series-h-tag-label")
        return SManga.create().apply {
            title = infoElement.selectFirst("h1 > span")!!.text()
            author = infoElement.selectFirst(".series-h-credit-user-item > .article-text")!!.text() // TODO: get 脚本/漫画
            description = infoElement.selectFirst(".series-h-credit-info-text-text > div > p > span > span")!!.text()
            thumbnail_url = "https:" + document.selectFirst(".series-h-img > picture > source")!!.attr("srcset")
            status = if (updateDateElement != null) SManga.ONGOING else SManga.COMPLETED
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        TODO("Not yet implemented")
    }

    override fun searchMangaFromElement(element: Element): SManga {
        TODO("Not yet implemented")
    }

    override fun searchMangaNextPageSelector(): String? {
        TODO("Not yet implemented")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("Not yet implemented")
    }

    override fun searchMangaSelector(): String {
        TODO("Not yet implemented")
    }
}

// // TODO: get manga status
// // TODO: filter by status
// // TODO: change cdnUrl as a array(upstream)
// class ComicGrowl : GigaViewer(
//    "コミックグロウル",
//    "https://comic-growl.com",
//    "all",
//    "https://cdn-img.comic-growl.com/public/page",
// ) {
//
//    override val publisher = "BUSHIROAD WORKS"
//
//    override val chapterListMode = CHAPTER_LIST_LOCKED
//
//    override val supportsLatest: Boolean = true
//
//    override val client: OkHttpClient =
//        super.client.newBuilder().addInterceptor(::imageIntercept).build()
//
//    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)
//
//    // Show only ongoing works
//    override fun popularMangaSelector(): String = "ul[class=\"lineup-list ongoing\"] > li > div > a"
//
//    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
//        title = element.select("h5").text()
//        thumbnail_url = element.select("div > img").attr("data-src")
//        setUrlWithoutDomain(element.attr("href"))
//    }
//
//    override fun latestUpdatesSelector() =
//        "div[class=\"update latest\"] > div.card-board > " + "div[class~=card]:not([class~=ad]) > div > a"
//
//    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
//        title = element.select("div.data h3").text()
//        thumbnail_url = element.select("div.thumb-container img").attr("data-src")
//        setUrlWithoutDomain(element.attr("href"))
//    }
//
//    override fun getCollections(): List<Collection> = listOf(
//        Collection("連載作品", ""),
//    )
//
//    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
//        if (query.isNotEmpty()) {
//            val url = "$baseUrl/search".toHttpUrl().newBuilder().addQueryParameter("q", query)
//
//            return GET(url.build(), headers)
//        }
//        return GET(baseUrl, headers) // Currently just get all ongoing works
//    }
// }

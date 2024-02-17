package eu.kanade.tachiyomi.extension.en.goodgirlsscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element

class GoodGirlsScan : Madara("Good Girls Scan", "https://goodgirls.moe", "en") {
    override val fetchGenres = false
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override fun searchMangaSelector() = "article.wp-manga"
    override fun searchMangaNextPageSelector() = "div.paginator .nav-next"
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"
    override val mangaDetailsSelectorDescription = "div.summary-specialfields"
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.vip-permission)"

    // heavily modified madara theme, throws 5xx errors on any search filter
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/${searchPage(page)}".toHttpUrl().newBuilder().apply {
            addQueryParameter("s", query.trim())
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList() = FilterList()

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.select(".entry-title a").let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        thumbnail_url = element.selectFirst(".post-thumbnail img")?.let(::imageFromElement)
    }
}

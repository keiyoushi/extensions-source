package eu.kanade.tachiyomi.extension.ja.mangaraworg

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class MangaRawOrg : MangaThemesia("Manga Raw.org", "https://mangaraw.org", "ja") {
    override val id = 6223520752496636410

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/search?order=popular&page=$page", headers)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/search?order=update&page=$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/search?s=$query&page=$page")

    override fun searchMangaSelector() = "div.bsx"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.bigor > a").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun searchMangaNextPageSelector() = "a[rel=next]"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document)
        .apply { description = document.select("div.bottom").firstOrNull()?.ownText() }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                pageListParse(response, baseUrl + chapter.url.removeSuffix("/"))
            }
    }

    private fun pageListParse(response: Response, chapterUrl: String): List<Page> {
        return response.asJsoup().select("span.page-link").first()!!.ownText().substringAfterLast(" ").toInt()
            .let { lastNum -> IntRange(1, lastNum) }
            .map { num -> Page(num, "$chapterUrl/$num") }
    }

    override fun imageUrlParse(document: Document): String = document.select("a.img-block img").attr("abs:src")
    override fun getFilterList(): FilterList = FilterList()
}

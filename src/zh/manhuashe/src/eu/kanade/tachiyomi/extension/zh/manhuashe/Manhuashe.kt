package eu.kanade.tachiyomi.extension.zh.manhuashe

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class Manhuashe : HttpSource() {

    override val supportsLatest: Boolean = true

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/order/hits/page/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.comic-list > div.comic-item").map { element: Element ->
            SManga.create().apply {
                title = element.selectFirst("h3 a")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")!!.attr("src")
            }
        }
        val nextPage = document.selectFirst("div.pagination > a.next")!!.attr("href")
        val currentPage = document.selectFirst("div.pagination > a.on")!!.attr("href")
        return MangasPage(mangas, nextPage != currentPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category/order/addtime/page/$page", headers)
    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/search/$query/$page", headers)
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("div.comic-meta-info > h1")!!.text()
            thumbnail_url = document.selectFirst("div.comic-cover-large > img")!!.absUrl("src")
            author = document.selectFirst("div.comic-stats > div.stat-item:contains(作者：)")!!.text().removePrefix("作者：")
            genre = document.selectFirst("div.comic-meta-info > div.comic-tags > span")?.text()?.replace(" ", ", ")
            description = document.selectFirst("div.comic-description > p")!!.text()
            status = when (document.select("div.comic-meta-info > div.comic-tags > span").last()?.text()) {
                "连载", "连载中" -> SManga.ONGOING
                "完结", "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chapter-list > div.chapter-item > a").map { element: Element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text()
            }
        }.asReversed()
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.comic-content > img").mapIndexed { index, it ->
            Page(index, imageUrl = it.attr("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

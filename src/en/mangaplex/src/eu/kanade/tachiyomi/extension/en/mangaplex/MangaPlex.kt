package eu.kanade.tachiyomi.extension.en.mangaplex

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaPlex : ParsedHttpSource() {

    override val name = "MangaPlex"
    override val baseUrl = "https://mangaplex.com"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/mangas/page/$page", headers)
    }

    override fun latestUpdatesNextPageSelector() = ".pages-nav a:contains(Next)"

    override fun latestUpdatesSelector() = "#posts-container li:has(.post-details)"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.thumbnail_url = element.select(".post-thumb img").attr("src")
        // using search for manga page and chapter list
        manga.url = element.select("h3.post-title a").attr("href").substringBeforeLast("-chapter").replace("$baseUrl/", "/search/").replace("-", "+")
        val mangaTitleSelector = element.select(".post-details p.post-excerpt").text().substringAfter("Read ").substringBefore(" Chapter")
        manga.title =
            if (mangaTitleSelector.contains("manga", true) || mangaTitleSelector.contains("manhwa", true) || mangaTitleSelector.contains("manhua", true)) {
                mangaTitleSelector.substringBeforeLast(" ")
            } else {
                mangaTitleSelector
            }
        return manga
    }

    // popular
    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector(): String = latestUpdatesNextPageSelector()

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/$query/page/$page")
    }

    override fun searchMangaSelector() = "#posts-container li:has(h5:contains(Manga)):has(p:contains(Chapter))"

    override fun searchMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector(): String = latestUpdatesNextPageSelector()

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply { /*empty*/ }

    // chapters
    override fun chapterListRequest(manga: SManga) = chapterListRequest(manga.url, 1)

    private fun chapterListRequest(mangaUrl: String, page: Int): Request {
        val mangaUrlClean = mangaUrl.removePrefix(baseUrl)
        return GET("$baseUrl$mangaUrlClean/page/$page", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        val chapters = document.select(chapterListSelector())
            .map(::chapterFromElement)
            .toMutableList()
        var nextPage = 2

        while (document.select(paginationNextPageSelector).isNotEmpty()) {
            val currentPage = document.select("meta[property=\"og:url\"]").attr("content")
            document = client.newCall(chapterListRequest(currentPage, nextPage)).execute().asJsoup()
            chapters += document.select(chapterListSelector())
                .map(::chapterFromElement)
            nextPage++
        }

        return chapters
    }

    private val paginationNextPageSelector = latestUpdatesNextPageSelector()

    override fun chapterListSelector() = searchMangaSelector()

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val chapterNameSelector = element.select(".post-title a").attr("title")
        chapter.setUrlWithoutDomain(element.select(".post-title a").attr("href"))
        chapter.name =
            if (chapterNameSelector.startsWith("chapter", true) && chapterNameSelector.contains("–")) {
                chapterNameSelector.substringBefore("–")
            } else {
                chapterNameSelector
            }

        return chapter
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("#the-post .entry-content > img").toList()
            .filter { it.attr("src").isNotEmpty() }
            .mapIndexed { i, el -> Page(i, "", el.attr("src")) }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")
}

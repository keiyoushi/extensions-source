package eu.kanade.tachiyomi.extension.fr.lirescan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
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
import rx.Observable

class LireScan : ParsedHttpSource() {

    override val name = "LireScan"

    override val baseUrl = "https://www.lirescan.me"

    override val lang = "fr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    // There's no proper directory, get list of manga from dropdown menu available from a manga's page
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/090-eko-to-issho-lecture-en-ligne/", headers)
    }

    override fun popularMangaSelector() = "div.form-group select option"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.text()
            url = element.attr("value")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = "ul#releases > h4"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.text()
            url = element.nextElementSibling()!!.select("a").first()!!.attr("href")
                .removeSuffix("/").dropLastWhile { it.isDigit() }
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return popularMangaRequest(1)
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val mangas = response.asJsoup().select(popularMangaSelector()).toList()
            .filter { it.text().contains(query, ignoreCase = true) }
            .map { searchMangaFromElement(it) }

        return MangasPage(mangas, false)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            thumbnail_url = document.select("a#imglink img").attr("abs:src")
        }
    }

    // Chapters

    override fun chapterListSelector() = "select#chapitres option"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.text().let { chNum ->
                name = "Chapter $chNum"
                setUrlWithoutDomain("${element.ownerDocument()!!.location()}$chNum/")
            }
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val lastPage = document.select("ul.pagination li.page-item:contains(Suiv)").first()!!
            .previousElementSibling()!!
            .text().toInt()

        return IntRange(1, lastPage).map { num -> Page(num - 1, document.location() + num) }
    }

    override fun imageUrlParse(document: Document): String {
        return document.select("a#imglink img").attr("abs:src")
    }

    override fun getFilterList() = FilterList()
}

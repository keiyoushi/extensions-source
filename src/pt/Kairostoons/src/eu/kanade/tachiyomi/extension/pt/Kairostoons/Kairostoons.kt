package eu.kanade.tachiyomi.extension.pt.Kairostoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Kairostoons : ParsedHttpSource() {

    override val name = "Kairostoons"
    override val baseUrl = "https://kairostoons.net"
    override val lang = "pt-BR"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    // =========================================================================
    //  Popular 
    // =========================================================================

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/manga/todos/"
        } else {
            "$baseUrl/manga/todos/page/$page/"
        }
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "article.comic-card, div.bsx"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst("a")!!
        setUrlWithoutDomain(anchor.attr("href"))

        title = element.selectFirst("h3.comic-card-title, .tt")?.text()?.trim()
            ?: anchor.attr("title")

        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun popularMangaNextPageSelector() = "a.next, a.next-page, div.pagination a.next"

    // =========================================================================
    //  Latest
    // =========================================================================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/?page=$page&order=update", headers)
    }

    override fun latestUpdatesSelector() = "div.bsx, div.bs"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =========================================================================
    //  Search
    // =========================================================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return Observable.fromCallable {
            val response = client.newCall(popularMangaRequest(page)).execute()
            val details = popularMangaParse(response)
            val mangas = details.mangas.filter {
                it.title.contains(query, true)
            }
            MangasPage(mangas, details.hasNextPage)
        }
    }

    override fun searchMangaSelector() = "div.bsx, div.bs"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // =========================================================================
    //  Manga Details
    // =========================================================================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.manga-title, h1.entry-title")!!.text()
        thumbnail_url = document.selectFirst("div.thumb img")?.attr("abs:src")

        description = document.selectFirst("div.manga-description, div.entry-content")?.text()

        genre = document.select(".mgen a, a.tag.genre-tag").joinToString { it.text() }

        fun getDetail(label: String): String? {
            return document.selectFirst("dt:contains($label) + dd")?.text()
                ?: document.selectFirst("table.infotable tr:contains($label) td:last-child")?.text()
        }

        author = getDetail("Autor")
        artist = getDetail("Artista") ?: author

        val statusText = getDetail("Status") ?: ""
        status = when {
            statusText.contains("Em lançamento", true) || statusText.contains("Ongoing", true) -> SManga.ONGOING
            statusText.contains("Completo", true) || statusText.contains("Completed", true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // =========================================================================
    //  Chapter List
    // =========================================================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.fromCallable {
            val chapters = mutableListOf<SChapter>()
            val visitedUrls = mutableSetOf<String>()

            var page = 1

            while (true) {
                val pageUrl = if (page == 1) "$baseUrl${manga.url}" else "$baseUrl${manga.url}?page=$page"

                val response = client.newCall(GET(pageUrl, headers)).execute()
                val document = Jsoup.parse(response.body.string(), response.request.url.toString())

                val pageChapters = document.select(chapterListSelector())
                    .map { chapterFromElement(it) }
                    .filter { visitedUrls.add(it.url) } 

                if (pageChapters.isEmpty()) break

                chapters.addAll(pageChapters)
                page++
            }
            chapters
        }
    }

    override fun chapterListSelector() = "li.chapter-item, div#chapterlist li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val anchor = element.selectFirst("a.chapter-link, a")!!
        setUrlWithoutDomain(anchor.attr("href"))

        name = element.selectFirst("span.chapter-number, span.chapternum")?.text()?.trim() ?: anchor.text()

        val dateText = element.selectFirst("span.chapter-date, span.chapterdate")?.text()?.trim() ?: ""
        date_upload = dateFormat.tryParse(dateText)
    }

    // =========================================================================
    //  Page List
    // =========================================================================

    override fun pageListParse(document: Document): List<Page> {
        val canvasElements = document.select("canvas.chapter-image-canvas")

        if (canvasElements.isNotEmpty()) {
            return canvasElements.mapIndexed { index, element ->
                Page(index, "", element.attr("abs:data-src-url"))
            }
        }

        return document.select("#readerarea img").mapIndexed { index, img ->
            val url = img.attr("abs:data-src").ifBlank { img.attr("abs:src") }
            Page(index, "", url)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}

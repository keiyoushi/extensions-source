package eu.kanade.tachiyomi.extension.en.comichubfree

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class ComicHubFree : Source, ParsedHttpSource() {
    override val baseUrl = "https://comichubfree.com"

    override val lang = "en"

    override val name = "ComicHubFree"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun popularMangaSelector() = ".movie-list-index > .cartoon-box"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaNextPageSelector() = "ul.pagination > li:last-child"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun chapterListSelector() = "div.episode-list > div > table > tbody > tr"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular-comic?page=$page")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/new-comic?page=$page")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search-comic?key=$query&page=$page")
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url + "/all")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.child(0).attr("abs:href"))
            title = element.selectFirst("h3")!!.text()
            val image = element.selectFirst("img")!!
            thumbnail_url = when {
                image.hasAttr("data-src") -> image.attr("abs:data-src")
                else -> image.attr("abs:src")
            }
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()!!
        val dateElement = element.select("td").last()!!

        val chapter = SChapter.create()
        chapter.name = urlElement.text()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.date_upload = dateElement.text().let {
            SimpleDateFormat("d-MMM-yyyy", Locale.getDefault()).parse(it)?.time ?: 0L
        }

        return chapter
    }

    private fun parseStatus(status: String): Int {
        if (status == "Ongoing") {
            return SManga.ONGOING
        } else if (status == "Completed") {
            return SManga.COMPLETED
        }

        return SManga.UNKNOWN
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.movie-info").first()!!
        val seriesInfoElement = infoElement.select("div.series-info")
        val seriesDescriptionElement = infoElement.select("div#film-content")

        val authorElement = seriesInfoElement.select("dt").firstOrNull { it.text().trim() == "Author:" }?.nextElementSibling()
        val statusElement = seriesInfoElement.select("dt").firstOrNull { it.text().trim() == "Status:" }?.nextElementSibling()

        val manga = SManga.create()
        manga.description = seriesDescriptionElement.text()
        val image = seriesInfoElement.select("img")
        manga.thumbnail_url = when {
            image.hasAttr("data-src") -> image.attr("abs:data-src")
            else -> image.attr("abs:src")
        }
        if (authorElement?.tagName() == "dd") {
            manga.author = authorElement.text()
        }
        if (statusElement?.tagName() == "dd") {
            manga.status = statusElement.text().orEmpty().let { parseStatus(it) }
        }

        return manga
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(super.mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { it -> mangaDetailsParse(it).apply { initialized = true } }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.chapter_img").mapIndexed { index, element ->
            val img = when {
                element.hasAttr("data-src") -> element.attr("abs:data-src")
                else -> element.attr("abs:src")
            }
            Page(index, imageUrl = img)
        }.distinctBy { it.imageUrl }
    }

    override fun imageUrlParse(document: Document) = ""
}

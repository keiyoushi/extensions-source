package eu.kanade.tachiyomi.extension.en.heartstrings

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Heartstrings : ParsedHttpSource() {

    final override val baseUrl: String = "https://heartstringscomic.com"
    final override val lang: String = "en"
    final override val name = "Heartstrings"
    final override val supportsLatest = false

    private val archive = "/archive"

    private val creator = "spiceparfait"

    private val synopsis = "Isidora Velasco is a beloved pop star with two big secrets: she used to be in a punk band called Velvet Crowbar, and she's a lesbian.\n" +
        "Heartstrings contains adult themes and is recommended for 18+ readers."

    final override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl$archive", headers)
    }

    final override fun popularMangaSelector(): String {
        return "div.archive-chapters"
    }

    final override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(archive)
            title = name
            thumbnail_url = element.select("img").first()?.attr("src")
            artist = creator
            author = creator
            status = SManga.ONGOING
            description = synopsis
        }
    }

    final override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(archive)
            title = name
            thumbnail_url = document.select("div.archive-chapters img").first()?.attr("src")
            artist = creator
            author = creator
            status = SManga.ONGOING
            description = synopsis
        }
    }

    final override fun chapterListSelector(): String {
        return "div.chapter"
    }

    final override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.select("div.chapter-more a").attr("href"))
            name = element.select("h3 a").text()
        }
    }

    final override fun pageListParse(document: Document): List<Page> {
        return document.select("div.archivecomic a")
            .mapIndexed { index, element ->
                Page(index, element.attr("abs:href"))
            }
    }

    final override fun imageUrlParse(document: Document): String {
        return document.select("img#comicimage").attr("abs:src")
    }

    final override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return Observable.just(MangasPage(emptyList(), false))
    }

    final override fun popularMangaNextPageSelector(): String? = null
    final override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()
    final override fun latestUpdatesNextPageSelector(): String? = null
    final override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
    final override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    final override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    final override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()
    final override fun searchMangaNextPageSelector(): String? = null
    final override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    final override fun searchMangaSelector(): String = throw UnsupportedOperationException()
}

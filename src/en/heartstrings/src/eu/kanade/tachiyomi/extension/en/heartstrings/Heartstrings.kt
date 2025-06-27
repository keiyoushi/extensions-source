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
    override val baseUrl: String = "https://heartstringscomic.com"
    override val lang: String = "en"
    override val name = "Heartstrings"
    override val supportsLatest = false

    private val synopsis = """
        Isidora Velasco is a beloved pop star with two big secrets: she used to be in a punk band called Velvet Crowbar, and she's a lesbian.
        "Heartstrings contains adult themes and is recommended for 18+ readers.
    """.trimIndent()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl$ARCHIVE_PATH", headers)
    }

    override fun popularMangaSelector(): String {
        return "div.archive-chapters"
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(ARCHIVE_PATH)
            title = name
            thumbnail_url = element.select("img").first()?.attr("src")
            artist = CREATOR
            author = CREATOR
            status = SManga.ONGOING
            description = synopsis
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(ARCHIVE_PATH)
            title = name
            thumbnail_url = document.select("div.archive-chapters img").first()?.attr("src")
            artist = CREATOR
            author = CREATOR
            status = SManga.ONGOING
            description = synopsis
        }
    }

    override fun chapterListSelector(): String {
        return "div.chapter"
    }

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.select("div.chapter-more a").attr("href"))
            name = element.select("h3 a").text()
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.archivecomic a")
            .mapIndexed { index, element ->
                Page(index, element.attr("abs:href"))
            }
    }

    override fun imageUrlParse(document: Document): String {
        return document.select("img#comicimage").attr("abs:src")
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return Observable.just(MangasPage(emptyList(), false))
    }

    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaSelector(): String = throw UnsupportedOperationException()

    companion object {
        private const val ARCHIVE_PATH = "/archive"
        private const val CREATOR = "spiceparfait"
    }
}

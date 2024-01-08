package eu.kanade.tachiyomi.extension.en.nuxscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class NuxScans : ParsedHttpSource() {

    override val name = "Nux Scans"
    override val baseUrl = "https://nuxscans.blogspot.com"
    private val baseUrl2 = "https://nuxscans-comics.blogspot.com"
    override val lang = "en"
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl2)
    }

    override fun popularMangaSelector() = "#Blog1 .hfeed .hentry .post-content"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select(".post-image-link img").attr("src")
        manga.url = element.select(".post-info .post-title a").attr("href").substringAfter(baseUrl2)
        manga.title = element.select(".post-info .post-tag").text()
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    // latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String = throw UnsupportedOperationException()

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaSelector(): String = throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector(): String = throw UnsupportedOperationException()

    // manga details
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl2 + manga.url)
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        thumbnail_url = document.select("a#unclick").attr("href")
        title = document.select("div.post-header .post-tag").text()
        description = document.select(".gridnux .column1 .text-overflow").joinToString("\n") { it.text() }
    }

    // chapters
    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl2 + manga.url)
    }

    override fun chapterListSelector() = ".column1 .text-truncate a"

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()!!
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = element.select("p").text()
        return chapter
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select(".post-content .separator a img").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""
}

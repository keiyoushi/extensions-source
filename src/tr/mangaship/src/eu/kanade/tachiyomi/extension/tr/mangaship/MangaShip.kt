package eu.kanade.tachiyomi.extension.tr.mangaship

import eu.kanade.tachiyomi.lib.dataimage.DataImageInterceptor
import eu.kanade.tachiyomi.lib.dataimage.dataImageAsUrl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaShip : ParsedHttpSource() {

    override val name = "Manga Ship"

    override val baseUrl = "https://www.mangaship.net"

    override val lang = "tr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(DataImageInterceptor())
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/Tr/PopulerMangalar?page=$page", headers)
    }

    override fun popularMangaSelector() = "div.movie-item-contents"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.movie-item-title a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "li.active + li a:not(.lastpage)"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/Tr/YeniMangalar?page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/Tr/Search?kelime=$query&tur=Manga&page=$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            thumbnail_url = document.select("div.dec-review-img img").attr("abs:src")
            genre = document.select("div.col-md-10 li:contains(Kategori) div a").joinToString { it.text() }
            author = document.select("div.col-md-10 li:contains(Yazar) div a").text()
            description = document.select("div.details-dectiontion p").joinToString("\n") { it.text() }
        }
    }

    // Chapters

    override fun chapterListSelector() = "div.item > div"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("div.plylist-single-content > a[title]").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reading-content-manga img").mapIndexed { i, img ->
            Page(i, "", img.dataImageAsUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()
}

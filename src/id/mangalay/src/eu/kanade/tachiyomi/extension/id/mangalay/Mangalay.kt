package eu.kanade.tachiyomi.extension.id.mangalay

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class Mangalay : HttpSource() {
    override val name = "Mangalay"
    override val baseUrl = "http://mangalay.blogspot.com"
    override val lang = "id"
    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/2013/04/daftar-baca-komik_20.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = Jsoup.parse(response.body.string(), baseUrl)
        val mangas = document.select(".post-body table").map { element: Element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.select("a").first()!!.absUrl("href"))
                title = element.select(".tr-caption").text()
                thumbnail_url = element.select("img").attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create()

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = Jsoup.parse(response.body.string(), baseUrl)
        return document.select(".post-body span > a").map { element: Element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.select("b").text()
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = Jsoup.parse(response.body.string(), baseUrl)
        return document.select(".separator img")
            .dropLast(1) // :last-child not working somehow
            .mapIndexed { index: Int, element: Element ->
                Page(index, imageUrl = element.attr("abs:src"))
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
}

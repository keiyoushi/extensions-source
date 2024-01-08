package eu.kanade.tachiyomi.extension.tr.mangatr

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class MangaTR : FMReader("Manga-TR", "https://manga-tr.com", "tr") {
    override fun headersBuilder() = super.headersBuilder()
        .add("Accept-Language", "en-US,en;q=0.5")

    override fun popularMangaNextPageSelector() = "div.btn-group:not(div.btn-block) button.btn-info"

    // =============================== Search ===============================
    // TODO: genre search possible but a bit of a pain
    override fun getFilterList() = FilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/arama.html?icerik=$query", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.use { it.asJsoup() }
            .select("div.row a[data-toggle]")
            .filterNot { it.siblingElements().text().contains("Novel") }
            .map(::searchMangaFromElement)

        return MangasPage(mangas, false)
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.text()
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val infoElement = document.selectFirst("div#tab1")!!
        infoElement.selectFirst("table + table tr + tr")?.run {
            author = selectFirst("td:nth-child(1) a")?.text()
            artist = selectFirst("td:nth-child(2) a")?.text()
            genre = selectFirst("td:nth-child(3)")?.text()
        }
        description = infoElement.selectFirst("div.well")?.ownText()?.trim()
        thumbnail_url = document.selectFirst("img.thumbnail")?.absUrl("src")

        status = infoElement.selectFirst("tr:contains(Çeviri Durumu) + tr > td:nth-child(2)")
            .let { parseStatus(it?.text()) }
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "tr.table-bordered"

    override val chapterUrlSelector = "td[align=left] > a"

    override val chapterTimeSelector = "td[align=right]"

    private val chapterListHeaders by lazy {
        headersBuilder().add("X-Requested-With", "XMLHttpRequest").build()
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val id = manga.url.substringAfter("manga-").substringBefore(".")
        val requestUrl = "$baseUrl/cek/fetch_pages_manga.php?manga_cek=$id"
        return client.newCall(GET(requestUrl, chapterListHeaders))
            .asObservableSuccess()
            .map(::chapterListParse)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        // chapters are paginated
        val chapters = buildList {
            val requestUrl = response.request.url.toString()
            var nextPage = 2
            do {
                val doc = when {
                    isEmpty() -> response
                    else -> {
                        val body = FormBody.Builder()
                            .add("page", nextPage.toString())
                            .build()
                        nextPage++
                        client.newCall(POST(requestUrl, chapterListHeaders, body)).execute()
                    }
                }.use { it.asJsoup() }

                addAll(doc.select(chapterListSelector()).map(::chapterFromElement))
            } while (doc.selectFirst("a[data-page=$nextPage]") != null)
        }
        return chapters
    }

    override fun pageListRequest(chapter: SChapter) =
        GET("$baseUrl/${chapter.url.substringAfter("cek/")}", headers)
}

package eu.kanade.tachiyomi.extension.it.zeurelscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ZeurelScan : HttpSource() {

    override val name = "ZeurelScan"

    override val baseUrl = "https://www.zeurelscan.com"

    override val lang = "it"

    override val supportsLatest = true

    // Caching results for search
    private val mangaList: MutableList<SManga> = mutableListOf()

    // Popular (not actually sorted as site has no such functionality)

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        mangaList.clear()
        response.asJsoup().select("a.titoliSerie").forEach {
            val manga = SManga.create().apply {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            mangaList.add(manga)
        }
        return MangasPage(mangaList, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        // The titles of all unique manga in the latest section
        val titles = document.select("div.capitoli_mini").distinctBy {
            it.selectFirst("div:not(:has(img))")!!.text()
        }.map { it.selectFirst("div:not(:has(img))")!!.text() }

        // Gets manga link from dropdown menu as latest section links directly to chapter
        val latestManga = document.select("a.titoliSerie").mapNotNull { element ->
            if (titles.any { title -> title.contains(element.text()) }) {
                SManga.create().apply {
                    setUrlWithoutDomain(element.attr("href"))
                    title = element.text()
                }
            } else {
                null
            }
        }
        return MangasPage(latestManga, false)
    }

    // Search from results retrieved by popularMangaRequest

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(
        MangasPage(mangaList.filter { it.title.contains(query, true) }, false),
    )

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        val info = document.selectFirst("div.testo")!!

        title = info.selectFirst("div.intestazione:contains(Titolo)")!!.nextElementSibling()!!.text()
        author = info.selectFirst("div.intestazione:contains(Autore)")!!.nextElementSibling()!!.text()
        artist = info.selectFirst("div.intestazione:contains(Artista)")!!.nextElementSibling()!!.text()
        genre = info.selectFirst("div.intestazione:contains(Genere)")!!.nextElementSibling()!!.text()
        description = info.selectFirst("div.intestazione:contains(Trama)")!!.nextElementSibling()!!.text()
        thumbnail_url = document.select("div.immagine > img").attr("abs:src")

        status = parseStatus(info.selectFirst("div.intestazione:contains(Stato)")!!.nextElementSibling()!!.text())
    }

    private fun parseStatus(status: String) = when {
        status.contains("In Corso", true) -> SManga.ONGOING
        status.contains("Completa", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val list = response.asJsoup().select("div.rigaCap")
        return list.map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst("a")!!.attr("abs:href"))
                name = it.selectFirst("div.titolo")!!.text()
                date_upload = parseChapterDate(it.selectFirst("div.data")!!.text())
                chapter_number = it.selectFirst("div.numCap")!!.text().substringAfter("#").toFloatOrNull() ?: 0f
            }
        }
    }

    private fun parseChapterDate(date: String): Long =
        try {
            SimpleDateFormat("d MMM yyyy", Locale.ITALIAN).parse(date)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }

    override fun pageListParse(response: Response): List<Page> =
        response.asJsoup().select("div.Immag img").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")
}

package eu.kanade.tachiyomi.extension.it.zeurelscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class ZeurelScan : HttpSource() {

    override val name = "ZeurelScan"

    override val baseUrl = "https://www.zeurelscan.com"

    override val lang = "it"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("dd/mm/yyyy", Locale.ITALY)

    // Caching results for search
    private val mangaList: MutableList<SManga> = mutableListOf()

    // Popular (not actually sorted as site has no such functionality)

    override fun popularMangaRequest(page: Int): Request = GET(
        baseUrl + "/series.php",
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        mangaList.clear()
        response.asJsoup().select("a.series-card").forEach {
            val manga = SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.select("span.series-title").text()
                thumbnail_url = it.select("img").attr("src")
            }
            mangaList.add(manga)
        }
        return MangasPage(mangaList, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET(
        baseUrl + "/ultimi.php",
        headers,
    )

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        // Cache titles for deduplication
        val titles = mutableListOf<String>()

        val latestManga = document.select("a.latest-row").mapNotNull { element ->
            // Deduplicate entries, add only not-already-seen
            if (!titles.any { title -> title.contains(element.select("span.latest-title").text()) }) {
                titles += element.select("span.latest-title").text()
                SManga.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    title = element.select("span.latest-title").text()
                    thumbnail_url = element.select("img.latest-thumb").attr("src")
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

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    // Details

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        val info = document.selectFirst("div.series-header")!!

        title = info.selectFirst("h1")!!.text().trim()
        author = info.selectFirst("p:contains(Autore)")!!.wholeOwnText().trim()
        artist = info.selectFirst("p:contains(Artista)")!!.wholeOwnText().trim()
        genre = info.selectFirst("p:contains(Genere)")!!.wholeOwnText().trim()
        description = info.selectFirst("p.series-plot")!!.text().trim()
        thumbnail_url = document.select("img").attr("abs:src")

        status = parseStatus(info.selectFirst("p:contains(Stato)")!!.wholeOwnText())
    }

    private fun parseStatus(status: String) = when {
        status.contains("In Corso", true) -> SManga.ONGOING
        status.contains("Completa", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val list = response.asJsoup().select("div.chapter")
        return list.map {
            val str = it.selectFirst("a")!!.wholeOwnText().substringAfter("#")

            // alternative (incremental, no side story handling)
            // val chapterNumStr = it.dataset().get("pagina")
            val chapterNumStr = str.substringBefore("–").trim()
            val chapterNum = if (chapterNumStr.contains("_")) {
                chapterNumStr.substringBefore("_").toFloat() + 0.1f
            } else {
                chapterNumStr.toFloat()
            }

            val tmpTitle = str.substringAfter("–").trim()
            val title = if (tmpTitle.length != 0) {
                chapterNumStr + " - " + tmpTitle
            } else {
                "Capitolo " + chapterNumStr
            }
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst("a")!!.absUrl("href"))
                name = title
                date_upload = dateFormat.tryParse(it.selectFirst("span.chapter-date")!!.text())
                chapter_number = chapterNum
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("div.reader img").mapIndexed { i, element ->
        Page(i, "", element.attr("abs:src"))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

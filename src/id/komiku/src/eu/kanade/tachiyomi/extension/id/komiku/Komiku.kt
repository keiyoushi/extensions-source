package eu.kanade.tachiyomi.extension.id.komiku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Komiku : HttpSource() {
    override val name = "Komiku"

    override val baseUrl = "https://komiku.org"

    private val baseUrlApi = "https://api.komiku.org"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = if (page == 1) {
        GET("$baseUrlApi/manga/?orderby=meta_value_num", headers)
    } else {
        GET("$baseUrlApi/manga/page/$page/?orderby=meta_value_num", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = if (page == 1) {
        GET("$baseUrlApi/manga/?orderby=modified", headers)
    } else {
        GET("$baseUrlApi/manga/page/$page/?orderby=modified", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrlApi.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            if (page > 1) {
                addPathSegments("page/$page")
            }

            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            }

            filters.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.code == 404) return MangasPage(emptyList(), false)
        return mangaListParse(response)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = Jsoup.parse(response.body.string(), baseUrl)
        return SManga.create().apply {
            description = document.select("#Sinopsis > p").text()

            document.select("table.inftable tr:contains(Judul Indonesia) td + td").text().let {
                if (it.isNotEmpty()) {
                    description = (if (description.isNullOrEmpty()) "" else "$description\n\n") + "Judul Indonesia: $it"
                }
            }

            author = document.select("table.inftable td:contains(Pengarang)+td, table.inftable td:contains(Komikus)+td").text().takeIf { it.isNotEmpty() }
            genre = document.select("ul.genre li.genre a span").joinToString { it.text() }.takeIf { it.isNotEmpty() }
            status = parseStatus(document.select("table.inftable tr > td:contains(Status) + td").text())
            thumbnail_url = document.selectFirst("div.ims > img")?.attr("abs:src")?.substringBefore("?")
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing", true) || status.contains("On Going", true) -> SManga.ONGOING
        status.contains("End", true) || status.contains("Completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ===============================
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = Jsoup.parse(response.body.string(), baseUrl)
        return document.select("#Daftar_Chapter tr:has(td.judulseries)").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                name = element.selectFirst("a")!!.text()

                val timeStamp = element.select("td.tanggalseries")
                date_upload = if (timeStamp.text().contains("lalu")) {
                    parseRelativeDate(timeStamp.text())
                } else {
                    dateFormat.tryParse(timeStamp.last()?.text())
                }
            }
        }
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    // Used Google translate here
    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.substringBefore(" lalu").removeSuffix("s").split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "jam" -> calendar.add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt())
            "menit" -> calendar.add(Calendar.MINUTE, -trimmedDate[0].toInt())
            "detik" -> calendar.add(Calendar.SECOND, 0)
        }

        return calendar.timeInMillis
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = Jsoup.parse(response.body.string(), baseUrl)
        return document.select("#Baca_Komik img").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = getKomikuFilterList()

    // ============================= Utilities ==============================
    private fun mangaListParse(response: Response): MangasPage {
        val document = Jsoup.parse(response.body.string(), baseUrl)
        val mangas = document.select("div.bge").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.text()
                setUrlWithoutDomain(element.selectFirst("a:has(h3)")!!.attr("href"))
                thumbnail_url = element.select("img").attr("abs:src").substringBefore("?")
            }
        }
        val hasNextPage = document.selectFirst("span[hx-get]") != null || mangas.size >= 10
        return MangasPage(mangas, hasNextPage)
    }
}

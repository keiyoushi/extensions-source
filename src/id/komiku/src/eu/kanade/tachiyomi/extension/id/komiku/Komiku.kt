package eu.kanade.tachiyomi.extension.id.komiku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Komiku : HttpSource() {
    override val name = "Komiku"

    override val baseUrl = "https://komiku.org"

    private val apiUrl = "https://api.komiku.org"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET(mangaApiUrlBuilder(page).addQueryParameter("orderby", "meta_value_num").build(), headers)

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET(mangaApiUrlBuilder(page).addQueryParameter("orderby", "modified").build(), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = mangaApiUrlBuilder(page).apply {
            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            }

            filters.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }
        }.build()

        return GET(url, headers)
    }

    private fun mangaApiUrlBuilder(page: Int) = apiUrl.toHttpUrl().newBuilder().apply {
        addPathSegment("manga")
        if (page > 1) {
            addPathSegments("page/$page")
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.code == 404) return MangasPage(emptyList(), false)
        return mangaListParse(response)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            description = buildString {
                append(document.select("#Sinopsis > p").text())

                document.selectFirst("table.inftable tr:contains(Judul Indonesia) td + td")?.text()?.let {
                    if (it.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("Judul Indonesia: $it")
                    }
                }
            }

            author = document.selectFirst("table.inftable td:contains(Pengarang)+td, table.inftable td:contains(Komikus)+td")?.text()
            genre = document.select("ul.genre li.genre a span").joinToString { it.text() }.takeIf { it.isNotEmpty() }
            status = parseStatus(document.selectFirst("table.inftable tr > td:contains(Status) + td")?.text())
            thumbnail_url = document.selectFirst("div.ims > img")?.absUrl("src")?.removeQuery()
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing", true) || status.contains("On Going", true) -> SManga.ONGOING
        status.contains("End", true) || status.contains("Completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ===============================
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#Daftar_Chapter tr:has(td.judulseries)").map { element ->
            SChapter.create().apply {
                val a = element.selectFirst("a")!!
                setUrlWithoutDomain(a.absUrl("href"))
                name = a.text()

                val timeStamp = element.selectFirst("td.tanggalseries")?.text().orEmpty()
                date_upload = if (timeStamp.contains("lalu")) {
                    parseRelativeDate(timeStamp)
                } else {
                    dateFormat.tryParse(timeStamp)
                }
            }
        }
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)

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
        val document = response.asJsoup()
        return document.select("#Baca_Komik img").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Type(),
        Order(),
        Genre1(),
        Genre2(),
        Status(),
    )

    // ============================= Utilities ==============================
    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.bge").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.text()
                setUrlWithoutDomain(element.selectFirst("a:has(h3)")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")?.removeQuery()
            }
        }
        val hasNextPage = document.selectFirst("span[hx-get]") != null || mangas.size >= 10
        return MangasPage(mangas, hasNextPage)
    }

    private fun String.removeQuery() = if (isEmpty()) this else toHttpUrl().newBuilder().query(null).build().toString()
}

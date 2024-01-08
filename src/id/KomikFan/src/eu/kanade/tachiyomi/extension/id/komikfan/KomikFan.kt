package eu.kanade.tachiyomi.extension.id.komikfan

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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class KomikFan : ParsedHttpSource() {

    override val name = "KomikFan"
    override val baseUrl = "https://komikfan.com"
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/daftar-komik", headers)
    }

    override fun popularMangaSelector() = "#a-z .row-cells .ranking1"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("a img").attr("data-src")
        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("a h4").text()
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga?page=$page", headers)
    }

    override fun latestUpdatesSelector() = ".daftar .bge"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleDash = element.select(".kan a.popunder").attr("href").substringAfter("manga/")

        manga.thumbnail_url = "$baseUrl/storage/komik/sampul_detail/$titleDash.jpg"
        manga.setUrlWithoutDomain(element.select(".kan a.popunder").attr("href"))
        manga.title = element.select(".kan a.popunder h3").text()

        return manga
    }

    override fun latestUpdatesNextPageSelector() = ".pagination a:contains(lanjut)"

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/searching?cari=$query")
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleDash = element.select("a").attr("href").substringAfter("manga/")

        manga.thumbnail_url = "$baseUrl/storage/komik/sampul_detail/$titleDash.jpg"
        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select(".kan h3").text()

        return manga
    }

    override fun searchMangaNextPageSelector(): String? = null

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        thumbnail_url = document.select("article section .ims img").attr("src")
        title = document.select(".inftable tbody ~ tr:contains(judul) b").text()
        author = document.select(".inftable td:contains(komikus) + td").firstOrNull()?.ownText()
        status = parseStatus(document.select(".inftable tr:contains(status)").text())
        genre = document.select("ul.genre li.genre a").joinToString { it.text() }
        description = document.select("#Sinopsis p").joinToString("\n") { it.text() }
    }

    private fun parseStatus(element: String): Int = when {
        element.lowercase().contains("ongoing") -> SManga.ONGOING
        element.lowercase().contains("completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapters
    override fun chapterListSelector() = "#Chapter > table tr:has(a)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("td.judulseries a").first()!!
        val chapter = SChapter.create()
        val mangaTitle = element.select("td.judulseries a span").text()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text().substringAfter(mangaTitle)
        chapter.date_upload = element.select("td.tanggalseries time").first()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return if (date.contains("yang lalu")) {
            val value = date.split(' ')[0].toInt()
            when {
                "detik" in date -> Calendar.getInstance().apply {
                    add(Calendar.SECOND, value * -1)
                }.timeInMillis
                "menit" in date -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                }.timeInMillis
                "jam" in date -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                }.timeInMillis
                "hari" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                }.timeInMillis
                "minggu" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                }.timeInMillis
                "bulan" in date -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                }.timeInMillis
                "tahun" in date -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, value * -1)
                }.timeInMillis
                else -> {
                    0L
                }
            }
        } else {
            try {
                dateFormat.parse(date)?.time ?: 0
            } catch (_: Exception) {
                0L
            }
        }
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("#Baca_Komik img").forEach { element ->
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

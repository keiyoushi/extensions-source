package eu.kanade.tachiyomi.extension.es.cerberusseries

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Calendar

class CerberusSeries : ParsedHttpSource() {

    override val name = "Cerberus Series"

    override val baseUrl = "https://cerberuseries.xyz"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/comics?page=$page", headers)

    override fun popularMangaSelector(): String = "div.grid > div:has(> div.c-iZMlIN)"

    override fun popularMangaNextPageSelector(): String = "nav[role=navigation] a:contains(»), nav[role=navigation] a:contains(Next)"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.c-hCLgme a").attr("href"))
        title = element.select("div.c-hCLgme a").text()
        thumbnail_url = element.selectFirst("div.c-iZMlIN img")?.attr("abs:src")
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw IOException("Esta funcionalidad aún no esta implementada.")

    override fun searchMangaSelector(): String = throw UnsupportedOperationException("Not used!")

    override fun searchMangaNextPageSelector(): String = throw UnsupportedOperationException("Not used!")

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used!")

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.selectFirst("div.thumb-wrapper img")!!.attr("abs:src")
        title = document.selectFirst("div.series-title")!!.text()
        genre = document.select("div.tags-container span").joinToString { it.text() }
        description = document.selectFirst("div.description-container")!!.text()
        author = document.select("div.useful-container p:containsOwn(Autor) strong").text()
    }

    override fun chapterListSelector(): String = "div.chapters-list-wrapper ul a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("li span")!!.text()
        date_upload = parseRelativeDate(element.selectFirst("li p")!!.text())
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.main-content p > img").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used!")

    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            WordSet("segundo").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            WordSet("minuto").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            WordSet("hora").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            WordSet("día", "dia").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            WordSet("semana").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            WordSet("mes").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            WordSet("año").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }

    class WordSet(private vararg val words: String) {
        fun anyWordIn(dateString: String): Boolean = words.any { dateString.contains(it, ignoreCase = true) }
    }
}

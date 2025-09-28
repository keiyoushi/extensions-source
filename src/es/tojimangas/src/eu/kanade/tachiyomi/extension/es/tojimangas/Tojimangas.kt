package eu.kanade.tachiyomi.extension.es.tojimangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.util.Calendar

class Tojimangas : ParsedHttpSource() {

    override val lang = "es"
    override val baseUrl = "https://tojimangas.com"
    override val name = "Tojimangas"
    override val supportsLatest = true

    override fun searchMangaSelector(): String = "a.poster"
    override fun popularMangaSelector(): String = searchMangaSelector()
    override fun latestUpdatesSelector(): String = ".recently-updated a.poster"

    override fun searchMangaNextPageSelector(): String = "a.next"
    override fun popularMangaNextPageSelector(): String = searchMangaNextPageSelector()
    override fun latestUpdatesNextPageSelector(): String = searchMangaNextPageSelector()

    override fun chapterListSelector(): String = ".item"

    private fun makeMangaRequest(
        page: Int,
        addToBuilder: (HttpUrl.Builder) -> HttpUrl.Builder,
    ): Request = GET(
        addToBuilder(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("page")
                addPathSegment(page.toString())
                addPathSegment("")
                addQueryParameter("post_type", "manga")
                addQueryParameter("s", "")
            },
        ).build(),
        headers,
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        makeMangaRequest(page) {
            it.setQueryParameter("s", query)
        }

    override fun popularMangaRequest(page: Int): Request = makeMangaRequest(page) {
        it.addQueryParameter("orderby", "popular")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()

        if (page > 1) {
            url.addPathSegment("page")
            url.addPathSegment(page.toString())
            url.addPathSegment("")
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("img")!!.attr("alt")
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("img")!!.attr("alt")
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val content = document.selectFirst(".content")
        thumbnail_url = content?.selectFirst("img")?.absUrl("src")
        genre = document.select(".meta a").joinToString { it.text() }
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val a = element.selectFirst("a")
        a?.absUrl("href")?.let { setUrlWithoutDomain(it) }
        name = a?.text() ?: ""
        date_upload = element.selectFirst(".time")?.text()?.trim()?.parseDate() ?: 0
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("#ch-images img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("data-src"))
        }
    }

    private fun String.parseDate(): Long {
        return try {
            parseRelativeDate(this)
        } catch (_: ParseException) {
            0L
        }
    }

    protected open fun parseRelativeDate(date: String): Long {
        val number = DATE_REGEX.find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            WordSet("segundo", "segundos").anyWordIn(date) -> cal.apply {
                add(
                    Calendar.SECOND,
                    -number,
                )
            }.timeInMillis

            WordSet("minuto", "minutos").anyWordIn(date) -> cal.apply {
                add(
                    Calendar.MINUTE,
                    -number,
                )
            }.timeInMillis

            WordSet("día", "días").anyWordIn(date) -> cal.apply {
                add(
                    Calendar.DAY_OF_MONTH,
                    -number,
                )
            }.timeInMillis

            WordSet("hora", "horas").anyWordIn(date) -> cal.apply {
                add(
                    Calendar.HOUR,
                    -number,
                )
            }.timeInMillis

            WordSet("semana", "semanas").anyWordIn(date) -> cal.apply {
                add(
                    Calendar.DAY_OF_MONTH,
                    -number * 7,
                )
            }.timeInMillis

            WordSet("mes", "meses").anyWordIn(date) -> cal.apply {
                add(
                    Calendar.MONTH,
                    -number,
                )
            }.timeInMillis

            WordSet("año", "años").anyWordIn(date) -> cal.apply {
                add(
                    Calendar.YEAR,
                    -number,
                )
            }.timeInMillis

            else -> 0
        }
    }

    class WordSet(private vararg val words: String) {
        fun anyWordIn(dateString: String): Boolean =
            words.any { dateString.contains(it, ignoreCase = true) }
    }

    override fun imageUrlParse(document: Document): String = ""

    companion object {
        private val DATE_REGEX = """(\d+)""".toRegex()
    }
}

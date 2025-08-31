package eu.kanade.tachiyomi.extension.pt.fenixmanhwas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.util.Calendar

class FenixManhwas : HttpSource() {

    override val name: String = "Fênix Manhwas"

    override val baseUrl: String = "https://fenixscan.xyz"

    override val lang: String = "pt-BR"

    override val supportsLatest: Boolean = true

    override val versionId: Int = 3

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    // ========================== Popular ==========================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".obra-slider a.obra-slide-item").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".obra-slide-title")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ========================== Latest ==========================

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#lancamentos-container .lancamento-bloco").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".lancamento-titulo")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(element.selectFirst("a.lancamento-thumb-link")!!.absUrl("href"))
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ========================== Search ==========================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return Observable.zip(fetchPopularManga(page), fetchLatestUpdates(page)) { popularPage, latestPage ->
            val distinctMangas = (popularPage.mangas + latestPage.mangas)
                .distinctBy(SManga::url)
                .filter { it.title.contains(query, ignoreCase = true) }
            MangasPage(distinctMangas, hasNextPage = false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    // ========================== Details ==========================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.obra-title")!!.text()
            thumbnail_url = document.selectFirst("img.obra-thumb")?.absUrl("src")
            description = document.selectFirst(".obra-excerpt")?.text()
            genre = document.select(".obra-genero").joinToString { it.text() }
            author = document.selectFirst(".info-pill:has(.mdi-account)")?.text()
            status = when (document.selectFirst(".info-pill:has(.mdi-information)")?.text()?.lowercase()) {
                "andamento" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    // ========================== Chapters ==========================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapters-grid > .chapter-item").map { element ->
            SChapter.create().apply {
                name = element.selectFirst(".chapter-title-text")!!.text()
                chapter_number = element.attr("data-num").toFloat()
                element.selectFirst("small")?.text()?.let(::parseRelativeDate)?.let {
                    date_upload = it
                }
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            }
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val number = DATE_NUMBER_REGEX.find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("segundo", ignoreCase = true) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            date.contains("minuto", ignoreCase = true) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("hora", ignoreCase = true) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("dia", ignoreCase = true) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("semana", ignoreCase = true) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number * 7) }.timeInMillis
            date.contains("mes", ignoreCase = true) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.contains("ano", ignoreCase = true) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }

    // ========================== Pages ==========================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".chapter-content img.lozad").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("data-src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val DATE_NUMBER_REGEX = """(\d+)""".toRegex()
    }
}

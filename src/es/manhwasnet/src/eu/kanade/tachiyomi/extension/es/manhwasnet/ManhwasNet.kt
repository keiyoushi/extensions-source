package eu.kanade.tachiyomi.extension.es.manhwasnet

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ManhwasNet : ParsedHttpSource() {

    override val baseUrl: String = "https://manhwas.net"
    override val lang: String = "es"
    override val name: String = "Manhwas.net"
    override val supportsLatest: Boolean = true

    override val client = network.client.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { sucuriInterceptor(it) }
        .build()

    private fun sucuriInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            // Try to clear cookies and retry
            client.cookieJar.saveFromResponse(url, emptyList())
            val clearHeaders = request.headers.newBuilder().removeAll("Cookie").build()
            chain.proceed(request.newBuilder().headers(clearHeaders).build())
        }
        if (response.headers["x-sucuri-cache"].isNullOrEmpty() && response.headers["x-sucuri-id"] != null && url.toString().startsWith(baseUrl)) {
            val script = response.asJsoup().selectFirst("script")?.data()
            if (script != null) {
                val a = script.split("(r)")[0].dropLast(1) + "r=r.replace('document.cookie','cookie');"
                QuickJs.create().use {
                    val b = it.evaluate(a) as String
                    val sucuriCookie = it.evaluate(b.replace("location.", "").replace("reload();", "")) as String
                    val cookieName = sucuriCookie.split("=")[0]
                    val cookieValue = sucuriCookie.split("=")[1].replace(";path", "")
                    client.cookieJar.saveFromResponse(url, listOf(Cookie.parse(url, "$cookieName=$cookieValue")!!))
                }
                val newResponse = chain.proceed(request)
                if (!newResponse.headers["x-sucuri-cache"].isNullOrEmpty()) return newResponse
            }
            throw IOException("Sitio protegido - Abra en WebView para intentar desbloquear.")
        }
        return response
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/biblioteca".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("page", page.toString())
        return GET(url.build().toString(), headers)
    }

    override fun popularMangaSelector() = "ul > li > article.anime"

    override fun popularMangaNextPageSelector() = "ul.pagination a.page-link[rel=next]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst(".title")!!.text()
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesNextPageSelector() = null

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").last()!!.attr("abs:href"))
        title = element.selectFirst(".title")!!.text()
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/biblioteca".toHttpUrlOrNull()!!.newBuilder()
        if (query.isNotEmpty()) {
            url.addQueryParameter("buscar", query)
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        url.addQueryParameter("genero", filter.toUriPart())
                    }
                    is OrderFilter -> {
                        url.addQueryParameter("estado", filter.toUriPart())
                    }

                    else -> {}
                }
            }
        }
        url.addQueryParameter("page", page.toString())
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document): SManga {
        val profileManga = document.selectFirst(".anime-single")!!
        return SManga.create().apply {
            title = profileManga.selectFirst(".title")!!.text()
            thumbnail_url = profileManga.selectFirst("img")!!.attr("abs:src")
            description = profileManga.selectFirst(".sinopsis")!!.text()
            status = parseStatus(profileManga.select("span.anime-type-peli").last()!!.text())
            genre = profileManga.select("p.genres > span").joinToString { it.text() }
        }
    }

    override fun chapterListSelector() = "ul.episodes-list > li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        name = element.selectFirst("a > div > p > span")!!.text()
        date_upload = parseRelativeDate(element.selectFirst("a > div > span")!!.text())
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("#chapter_imgs img[src][src!=\"\"]").mapIndexed { i, img ->
            val url = img.attr("abs:src")
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used.")

    override fun getFilterList() = FilterList(
        Filter.Header("Los filtros no se pueden combinar:"),
        Filter.Header("Prioridad:   Texto > Géneros > Estado"),
        Filter.Separator(),
        GenreFilter(),
        OrderFilter(),
    )

    private fun parseStatus(status: String): Int = when (status) {
        "Publicándose" -> SManga.ONGOING
        "Finalizado" -> SManga.COMPLETED
        "Cancelado" -> SManga.CANCELLED
        "Pausado" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            WordSet("segundo").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            WordSet("minuto").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            WordSet("hora").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            WordSet("día").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
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

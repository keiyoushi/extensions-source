package eu.kanade.tachiyomi.extension.es.manhuako

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.Calendar

class ManhuaKO : ParsedHttpSource() {

    override val baseUrl = "https://manhuako.com"

    override val lang = "es"

    override val name = "ManhuaKO"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() =
        "div#Manhua-week .my-carousel-item, div#Manhwa-week .my-carousel-item, div#Manga-week .my-carousel-item"

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        with(element.selectFirst("> a")!!) {
            setUrlWithoutDomain(attr("href"))
            title = selectFirst("img")!!.attr("title")
            thumbnail_url = selectFirst("img")!!.imgAttr()
        }
    }

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesSelector() =
        "div#Manhua-recent .my-carousel-item, div#Manhwa-recent .my-carousel-item, div#Manga-recent .my-carousel-item"

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addPathSegment("home")
            urlBuilder.addPathSegment("search")
            urlBuilder.addQueryParameter("mq", query)
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is TypeFilter -> {
                        if (filter.state == 0) {
                            urlBuilder.addPathSegment("home")
                            urlBuilder.addPathSegment("search")
                        } else {
                            urlBuilder.addPathSegment(filter.toUriPart())
                        }
                    }
                    is GenreFilter -> {
                        if (filter.state != 0) {
                            urlBuilder.addPathSegment("genre")
                            urlBuilder.addPathSegment(filter.toUriPart())
                        }
                    }
                    else -> {}
                }
            }
        }

        urlBuilder.addPathSegment("page")
        urlBuilder.addPathSegment(page.toString())

        return GET(urlBuilder.build(), headers)
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("Los filtros serán ignorados si se realiza una búsqueda por texto"),
            Filter.Separator(),
            TypeFilter(),
            GenreFilter(),
        )
    }

    override fun searchMangaSelector() = "div.card-image"

    override fun searchMangaNextPageSelector() = "ul.pagination li.active + li"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("section > div.container > h1.center-align").text()
        genre = document.select("div.card-panel div:has(> span:contains(Genero)) a.chip").joinToString { it.ownText() }
        description = document.select("div.card-panel p").text()
        thumbnail_url = document.select("section > div.container img#preview.fit-img").imgAttr()
        author = document.selectFirst("div.card-panel div:has(> span:contains(Creador)) > a")!!.ownText()
    }

    override fun chapterListSelector() = "table.table-chapters tr"

    private fun chapterListNextPageSelector() = "ul.pagination li i:contains(last_page), ul.pagination li.active + li"

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url.toString()
        var document = response.asJsoup()

        val chapters = mutableListOf<SChapter>()
        chapters.addAll(document.select(chapterListSelector()).map { chapterFromElement(it) })

        var page = 2
        while (!document.select(chapterListNextPageSelector()).isEmpty()) {
            document = client.newCall(GET("$url/page/$page", headers)).execute().asJsoup()
            chapters.addAll(document.select(chapterListSelector()).map { chapterFromElement(it) })
            page++
        }
        return chapters
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = "Capítulo " + element.select("a").text()
        date_upload = parseRelativeDate(element.selectFirst("span.truncate")!!.ownText())
        setUrlWithoutDomain(element.select("a").attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#pantallaCompleta img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String {
        return when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            else -> attr("abs:src")
        }
    }

    private fun Elements.imgAttr() = this.first()!!.imgAttr()

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

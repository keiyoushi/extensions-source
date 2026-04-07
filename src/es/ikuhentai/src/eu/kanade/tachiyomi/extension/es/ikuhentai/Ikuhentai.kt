package eu.kanade.tachiyomi.extension.es.ikuhentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Ikuhentai : ParsedHttpSource() {
    override val name = "Ikuhentai"
    override val baseUrl = "https://ikuhentai.net"
    override val lang = "es"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private val dateFormat by lazy {
        SimpleDateFormat("MMMM dd, yyyy", Locale("es"))
    }

    override fun popularMangaRequest(page: Int): Request {
        val pagePath = if (page > 1) "page/$page/" else ""
        return GET("$baseUrl/$pagePath?s=&post_type=wp-manga&m_orderby=views", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val pagePath = if (page > 1) "page/$page/" else ""
        return GET("$baseUrl/$pagePath?s=&post_type=wp-manga&m_orderby=latest", headers)
    }

    override fun popularMangaSelector() = "div.page-listing-item .page-item-detail, div.c-tabs-item__content"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    // WordPress switches pagination structure on subsequent pages and search pages
    override fun popularMangaNextPageSelector() = "a.nextpostslink, div.nav-previous > a"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val img: Element? = element.selectFirst("img")
        manga.thumbnail_url = img?.let {
            it.absUrl("data-lazy-src").ifEmpty { it.absUrl("src") }
        }

        val link: Element? = element.selectFirst("div.item-thumb > a, div.tab-thumb > a")
        if (link != null) {
            manga.setUrlWithoutDomain(link.absUrl("href"))
            manga.title = link.attr("title").ifEmpty { link.text() }
        }
        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
                addPathSegment("") // for trailing slash
            }
            addQueryParameter("s", query)
            addQueryParameter("post_type", "wp-manga")

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is GenreList -> {
                        filter.state.filter { it.state == 1 }.forEach {
                            addQueryParameter("genre[]", it.id)
                        }
                    }
                    is StatusList -> {
                        filter.state.filter { it.state == 1 }.forEach {
                            addQueryParameter("status[]", it.id)
                        }
                    }
                    is SortBy -> {
                        val orderBy = filter.toUriPart()
                        if (orderBy.isNotEmpty()) {
                            addQueryParameter("m_orderby", orderBy)
                        }
                    }
                    is TextField -> {
                        if (filter.state.isNotEmpty()) {
                            addQueryParameter(filter.key, filter.state)
                        }
                    }
                    else -> {}
                }
            }
        }

        return GET(url.build(), headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement: Element = document.selectFirst("div.site-content") ?: document

        val manga = SManga.create()
        manga.author = infoElement.select("div.author-content").text()
        manga.artist = infoElement.select("div.artist-content").text()

        val genres = infoElement.select("div.genres-content a").map { it.text() }
        manga.genre = genres.joinToString(", ")

        val statusText = infoElement.select("div.post-content_item:has(h5:contains(Estado)) div.summary-content").text()
        manga.status = parseStatus(statusText)

        manga.description = document.select("div.description-summary").text()

        val img: Element? = document.selectFirst("div.summary_image img")
        manga.thumbnail_url = img?.let {
            it.absUrl("data-lazy-src").ifEmpty { it.absUrl("src") }
        }

        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.lowercase().contains("ongoing") || element.lowercase().contains("emisión") || element.lowercase().contains("emision") -> SManga.ONGOING
        element.lowercase().contains("completado") || element.lowercase().contains("finalizado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = (baseUrl + manga.url).removeSuffix("/")
        return POST("$url/ajax/chapters/", headers)
    }

    override fun chapterListSelector() = "li.wp-manga-chapter"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement: Element = element.selectFirst("a")!!
        val url = urlElement.absUrl("href").toHttpUrl().newBuilder().apply {
            removeAllQueryParameters("style")
            addQueryParameter("style", "list")
        }.build().toString()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(url)
        chapter.name = urlElement.text()

        val dateElement: Element? = element.selectFirst("span.chapter-release-date i")
        dateElement?.let {
            chapter.date_upload = dateFormat.tryParse(it.text())
        }

        return chapter
    }

    override fun pageListParse(document: Document): List<Page> = document.select("div.reading-content * img").mapIndexed { i, element ->
        val url = element.absUrl("data-lazy-src").ifEmpty { element.absUrl("src") }
        Page(i, "", url)
    }.filter { it.imageUrl!!.isNotEmpty() }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used.")

    override fun imageRequest(page: Page): Request {
        val imgHeader = headers.newBuilder().apply {
            add("Referer", "$baseUrl/")
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

    private class TextField(name: String, val key: String) : Filter.Text(name)

    private class SortBy :
        UriPartFilter(
            "Ordenar por",
            arrayOf(
                Pair("Relevance", ""),
                Pair("Latest", "latest"),
                Pair("A-Z", "alphabet"),
                Pair("Calificación", "rating"),
                Pair("Tendencia", "trending"),
                Pair("Más visto", "views"),
                Pair("Nuevo", "new-manga"),
            ),
        )

    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    private class Status(name: String, val id: String = name) : Filter.TriState(name)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Estado", statuses)

    override fun getFilterList() = FilterList(
        TextField("Autor", "author"),
        TextField("Año de publicación", "release"),
        SortBy(),
        StatusList(getStatusList()),
        GenreList(getGenreList()),
    )

    private fun getStatusList() = listOf(
        Status("Completado", "end"),
        Status("En emisión", "on-going"),
        Status("Cancelado", "canceled"),
        Status("Pausado", "on-hold"),
    )

    private fun getGenreList() = listOf(
        Genre("Ahegao", "ahegao"),
        Genre("Anal", "anal"),
        Genre("Bestiality", "bestialidad"),
        Genre("Bondage", "bondage"),
        Genre("Bukkake", "bukkake"),
        Genre("Chicas monstruo", "chicas-monstruo"),
        Genre("Chikan", "chikan"),
        Genre("Colegialas", "colegialas"),
        Genre("Comics porno", "comics-porno"),
        Genre("Dark Skin", "dark-skin"),
        Genre("Demonios", "demonios"),
        Genre("Ecchi", "ecchi"),
        Genre("Embarazadas", "embarazadas"),
        Genre("Enfermeras", "enfermeras"),
        Genre("Eroges", "eroges"),
        Genre("Fantasía", "fantasia"),
        Genre("Futanari", "futanari"),
        Genre("Gangbang", "gangbang"),
        Genre("Gemelas", "gemelas"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Gore", "gore"),
        Genre("Handjob", "handjob"),
        Genre("Harem", "harem"),
        Genre("Hipnosis", "hipnosis"),
        Genre("Incesto", "incesto"),
        Genre("Loli", "loli"),
        Genre("Maids", "maids"),
        Genre("Masturbación", "masturbacion"),
        Genre("Milf", "milf"),
        Genre("Mind Break", "mind-break"),
        Genre("My Hero Academia", "my-hero-academia"),
        Genre("Naruto", "naruto"),
        Genre("Netorare", "netorare"),
        Genre("Paizuri", "paizuri"),
        Genre("Pokemon", "pokemon"),
        Genre("Profesora", "profesora"),
        Genre("Prostitución", "prostitucion"),
        Genre("Romance", "romance"),
        Genre("Straight Shota", "straight-shota"),
        Genre("Tentáculos", "tentaculos"),
        Genre("Virgen", "virgen"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}

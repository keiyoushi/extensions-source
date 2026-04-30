package eu.kanade.tachiyomi.extension.es.ikuhentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
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
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Ikuhentai : HttpSource() {
    override val name = "Ikuhentai"
    override val baseUrl = "https://ikuhentai.net"
    override val lang = "es"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))

    override fun popularMangaRequest(page: Int): Request {
        val pagePath = if (page > 1) "page/$page/" else ""
        return GET("$baseUrl/$pagePath?s=&post_type=wp-manga&m_orderby=views", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val pagePath = if (page > 1) "page/$page/" else ""
        return GET("$baseUrl/$pagePath?s=&post_type=wp-manga&m_orderby=latest", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.page-listing-item .page-item-detail, div.c-tabs-item__content").map {
            mangaFromElement(it)
        }
        val hasNextPage = document.selectFirst("a.nextpostslink, div.nav-previous > a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val img = element.selectFirst("img")
        manga.thumbnail_url = img?.let {
            it.absUrl("data-lazy-src").ifEmpty { it.absUrl("src") }
        }

        val link = element.selectFirst("div.item-thumb > a, div.tab-thumb > a")
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
                        filter.state.filter { it.state == Filter.TriState.STATE_INCLUDE }.forEach {
                            addQueryParameter("genre[]", it.id)
                        }
                    }
                    is StatusList -> {
                        filter.state.filter { it.state == Filter.TriState.STATE_INCLUDE }.forEach {
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

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("div.site-content") ?: document

        val manga = SManga.create()
        manga.author = infoElement.select("div.author-content").text()
        manga.artist = infoElement.select("div.artist-content").text()

        val genres = infoElement.select("div.genres-content a").map { it.text() }
        manga.genre = genres.joinToString(", ")

        val statusText = infoElement.select("div.post-content_item:has(h5:contains(Estado)) div.summary-content").text()
        manga.status = parseStatus(statusText)

        manga.description = document.select("div.description-summary").text()

        val img = document.selectFirst("div.summary_image img")
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

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("li.wp-manga-chapter").map { element ->
            val urlElement = element.selectFirst("a")!!
            val url = urlElement.absUrl("href").toHttpUrl().newBuilder().apply {
                removeAllQueryParameters("style")
                addQueryParameter("style", "list")
            }.build().toString()

            SChapter.create().apply {
                setUrlWithoutDomain(url)
                name = urlElement.text()

                val dateElement = element.selectFirst("span.chapter-release-date i")
                if (dateElement != null) {
                    date_upload = dateFormat.tryParse(dateElement.text())
                }
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.reading-content * img").mapIndexedNotNull { i, element ->
            val url = element.absUrl("data-lazy-src").ifEmpty { element.absUrl("src") }
            if (url.isNotEmpty()) Page(i, imageUrl = url) else null
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    override fun imageRequest(page: Page): Request {
        val imgHeader = headers.newBuilder().apply {
            add("Referer", "$baseUrl/")
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

    override fun getFilterList() = FilterList(
        TextField("Autor", "author"),
        TextField("Año de publicación", "release"),
        SortBy(),
        StatusList(getStatusList()),
        GenreList(getGenreList()),
    )
}

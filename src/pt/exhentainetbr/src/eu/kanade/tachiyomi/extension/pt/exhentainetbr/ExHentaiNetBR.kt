package eu.kanade.tachiyomi.extension.pt.exhentainetbr

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class ExHentaiNetBR : HttpSource() {

    override val name = "ExHentai.net.br"

    override val baseUrl = "https://exhentai.net.br"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/lista-de-mangas/page/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.itemP").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.ownText()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            }
        }
        val hasNextPage = document.selectFirst(".content-pagination li.active + li:not(.next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/page/$page".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()

        val filter = filters.firstInstanceOrNull<AlphabetFilter>()

        if (query.isBlank() && filter != null && filter.selected() != DEFAULT_FILTER_VALUE) {
            url = "$baseUrl/lista-de-mangas".toHttpUrl().newBuilder()
                .addQueryParameter("letra", filter.selected())
                .build()
        }
        return GET(url, headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(PREFIX_SEARCH)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val slug = query.substringAfter(PREFIX_SEARCH)
        return client.newCall(GET("$baseUrl/manga/$slug", headers))
            .asObservableSuccess()
            .map { MangasPage(listOf(mangaDetailsParse(it)), false) }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val isLetterSearch = response.request.url.queryParameter("letra") != null
        val selector = if (isLetterSearch) "article.itemP" else ".post article.itemP"

        val mangas = document.select(selector).map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.ownText()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            }
        }
        val hasNextPage = document.selectFirst(".content-pagination li.active + li:not(.next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".stats_box h3")!!.text()
            description = document.selectFirst(".sinopse_manga .info_p:last-child")?.text()
            thumbnail_url = document.selectFirst(".anime_cover img")?.imgAttr()
            artist = document.selectFirst(".sinopse_manga h5:contains(Artista) + span")?.text()
            author = artist
            genre = document.select(".tag-btn").joinToString { it.ownText() }
            val statusLabel = document.selectFirst(".stats_box span")?.ownText() ?: ""
            status = when {
                statusLabel.equals("Completo", ignoreCase = true) -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            setUrlWithoutDomain(document.location())
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapter_content a").map { element ->
            SChapter.create().apply {
                name = element.selectFirst(".name_chapter")!!.text()
                val dateStr = element.selectFirst("span.release-date")?.ownText()?.substringAfter(":")?.trim()
                date_upload = dateFormat.tryParse(dateStr)
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.manga_image > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        val alphabet = mutableListOf(DEFAULT_FILTER_VALUE).also {
            it += ('A'..'Z').map { char -> "$char" }
        }

        return FilterList(
            Filter.Header(
                """
                    Busca por título possue prioridade.
                    Deixe em branco para pesquisar por letra
                """.trimIndent(),
            ),
            AlphabetFilter("Alfabeto", alphabet),
        )
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    companion object {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        const val PREFIX_SEARCH = "id:"
        const val DEFAULT_FILTER_VALUE = "Padrão"
    }
}

class AlphabetFilter(
    displayName: String,
    private val vals: List<String>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.toTypedArray(), state) {
    fun selected() = vals[state]
}

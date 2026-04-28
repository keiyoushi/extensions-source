package eu.kanade.tachiyomi.extension.pt.brasilhentai

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable

class BrasilHentai : HttpSource() {

    override val name = "Brasil Hentai"

    override val baseUrl = "https://brasilhentai.com"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(4)
        .build()

    private var categoryFilterOptions: Array<Pair<String, String>> = emptyArray()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (categoryFilterOptions.isEmpty()) {
            categoryFilterOptions = parseCategories(document)
        }
        val mangas = document.select(".content-area article").map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst("a[title]")!!
                title = anchor.attr("title")
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                initialized = true
                setUrlWithoutDomain(anchor.absUrl("href"))
            }
        }
        val hasNextPage = document.selectFirst(".next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selectedValue() ?: ""

        val url = if (category.isEmpty()) {
            "$baseUrl/page/$page".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .build()
        } else {
            "$baseUrl/category/$category/page/$page/".toHttpUrl()
        }

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(SEARCH_PREFIX)) {
            val url = "$baseUrl/${query.substringAfter(SEARCH_PREFIX)}/"
            return client.newCall(GET(url, headers))
                .asObservableSuccess()
                .map {
                    val manga = mangaDetailsParse(it).apply {
                        setUrlWithoutDomain(url)
                    }
                    MangasPage(listOf(manga), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.ownText()
            thumbnail_url = document.selectFirst(".entry-content p a img")?.absUrl("src")
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(
        listOf(
            SChapter.create().apply {
                name = "Capítulo único"
                url = manga.url
            },
        ),
    )

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".entry-content p > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()

        filters += if (categoryFilterOptions.isNotEmpty()) {
            CategoryFilter("Categoria", categoryFilterOptions)
        } else {
            Filter.Header("Aperte 'Redefinir' para tentar mostrar as categorias")
        }

        return FilterList(filters)
    }

    private fun parseCategories(document: Document): Array<Pair<String, String>> = document.select("#categories-2 li a").map { element ->
        val url = element.absUrl("href")
        val category = url.split("/").filter { it.isNotBlank() }.last()
        Pair(element.ownText(), category)
    }.toTypedArray()

    companion object {
        const val SEARCH_PREFIX: String = "slug:"
    }
}

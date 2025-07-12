package eu.kanade.tachiyomi.multisrc.mccms

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import rx.Observable

open class MCCMSWeb(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String = "zh",
    protected val config: MCCMSConfig = MCCMSConfig(),
) : HttpSource() {
    override val supportsLatest get() = true

    init {
        Intl.lang = lang
    }

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.request.url.encodedPath == "/err/comic") {
                    throw IOException(response.body.string().substringBefore('\n'))
                }
                response
            }
            .build()
    }

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", System.getProperty("http.agent")!!)

    open fun parseListing(document: Document): MangasPage {
        parseGenres(document, config.genreData)
        val mangas = document.select(simpleMangaSelector()).map(::simpleMangaFromElement)
        val hasNextPage = run { // default pagination
            val buttons = document.selectFirst("#Pagination, .NewPages")!!.select(Evaluator.Tag("a"))
            val count = buttons.size
            // Next page != Last page
            buttons[count - 1].attr("href") != buttons[count - 2].attr("href")
        }
        return MangasPage(mangas, hasNextPage)
    }

    open fun simpleMangaSelector() = ".common-comic-item"

    open fun simpleMangaFromElement(element: Element) = SManga.create().apply {
        val titleElement = element.selectFirst(Evaluator.Class("comic__title"))!!.child(0)
        url = titleElement.attr("href").removePathPrefix()
        title = titleElement.ownText()
        thumbnail_url = element.selectFirst(Evaluator.Tag("img"))!!.attr("data-original")
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/order/hits/page/$page", pcHeaders)

    override fun popularMangaParse(response: Response) = parseListing(response.asJsoup())

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category/order/addtime/page/$page", pcHeaders)

    override fun latestUpdatesParse(response: Response) = parseListing(response.asJsoup())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        if (query.isNotBlank()) {
            val url = if (config.textSearchOnlyPageOne) {
                "$baseUrl/search".toHttpUrl().newBuilder()
                    .addQueryParameter("key", query)
                    .toString()
            } else {
                "$baseUrl/search/$query/$page"
            }
            GET(url, pcHeaders)
        } else {
            val url = buildString {
                append(baseUrl).append("/category/")
                filters.filterIsInstance<MCCMSFilter>().map { it.query }.filter { it.isNotEmpty() }
                    .joinTo(this, "/")
                append("/page/").append(page)
            }
            GET(url, pcHeaders)
        }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (document.selectFirst(Evaluator.Id("code-div")) != null) {
            val manga = SManga.create().apply {
                url = "/search"
                title = "验证码"
                description = "请点击 WebView 按钮输入验证码，完成后返回重新搜索"
                initialized = true
            }
            return MangasPage(listOf(manga), false)
        }
        val result = parseListing(document)
        if (config.textSearchOnlyPageOne && document.location().contains("search")) {
            return MangasPage(result.mangas, false)
        }
        return result
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        if (manga.url == "/search") return Observable.just(manga)
        return super.fetchMangaDetails(manga)
    }

    override fun getMangaUrl(manga: SManga) = baseUrl.mobileUrl() + manga.url

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, pcHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        return run {
            SManga.create().apply {
                val document = response.asJsoup().selectFirst(Evaluator.Class("de-info__box"))!!
                title = document.selectFirst(Evaluator.Class("comic-title"))!!.ownText()
                thumbnail_url = document.selectFirst(Evaluator.Tag("img"))!!.attr("src")
                author = document.selectFirst(Evaluator.Class("name"))!!.text()
                genre = document.selectFirst(Evaluator.Class("comic-status"))!!.select(Evaluator.Tag("a")).joinToString { it.ownText() }
                description = document.selectFirst(Evaluator.Class("intro-total"))!!.text()
            }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        if (manga.url == "/search") return Observable.just(emptyList())
        return super.fetchChapterList(manga)
    }

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url, pcHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        return getDescendingChapters(
            response.asJsoup().select(chapterListSelector()).map {
                val link = it.child(0)
                SChapter.create().apply {
                    url = link.attr("href").removePathPrefix()
                    name = link.text()
                }
            },
        )
    }

    open fun chapterListSelector() = ".chapter__list-box > li"

    open fun getDescendingChapters(chapters: List<SChapter>) = chapters.asReversed()

    override fun getChapterUrl(chapter: SChapter) = baseUrl.mobileUrl() + chapter.url

    override fun pageListRequest(chapter: SChapter): Request =
        GET(baseUrl + chapter.url, if (config.useMobilePageList) headers else pcHeaders)

    override fun pageListParse(response: Response) = config.pageListParse(response)

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Don't send referer
    override fun imageRequest(page: Page) = GET(page.imageUrl!!, pcHeaders)

    override fun getFilterList(): FilterList {
        val genreData = config.genreData
        return getWebFilters(genreData)
    }
}

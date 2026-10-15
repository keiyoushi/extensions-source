package eu.kanade.tachiyomi.multisrc.mccms

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.IOException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator

abstract class MCCMSWeb : KeiSource() {

    protected open val config: MCCMSConfig = MCCMSConfig()

    init {
        Intl.lang = lang
    }

    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.request.url.encodedPath == "/err/comic") {
            throw IOException(response.body.string().substringBefore('\n'))
        }
        response
    }
        .rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    override fun Headers.Builder.configureHeaders() = set("User-Agent", System.getProperty("http.agent")!!)
        .removeAll("Referer")
        .removeAll("Origin")

    open fun parseListing(document: Document): MangasPage {
        val mangas = document.select(simpleMangaSelector()).map(::simpleMangaFromElement)
        val hasNextPage = run {
            // default pagination
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

    override suspend fun getPopularManga(page: Int) = parseListing(client.get("$baseUrl/category/order/hits/page/$page", pcHeaders).asJsoup())

    override suspend fun getLatestUpdates(page: Int) = parseListing(client.get("$baseUrl/category/order/addtime/page/$page", pcHeaders).asJsoup())

    protected open val searchHeaders: Headers get() = pcHeaders

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            if (config.textSearchOnlyPageOne) {
                "$baseUrl/search".toHttpUrl().newBuilder()
                    .addQueryParameter("key", query)
                    .toString()
            } else {
                "$baseUrl/search/$query/$page"
            }
        } else {
            buildString {
                append(baseUrl).append("/category/")
                filters.filterIsInstance<MCCMSFilter>().map { it.query }.filter { it.isNotEmpty() }
                    .joinTo(this, "/")
                append("/page/").append(page)
            }
        }
        return searchMangaParse(client.get(url, searchHeaders).asJsoup())
    }

    protected open fun searchMangaParse(document: Document): MangasPage {
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

    override fun getMangaUrl(manga: SManga) = baseUrl.mobileUrl() + manga.url

    // details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (manga.url == "/search") return SMangaUpdate(manga, emptyList())
        val document = fetchMangaPage(manga)
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    protected open suspend fun fetchMangaPage(manga: SManga): Document = client.get(baseUrl + manga.url, pcHeaders).asJsoup()

    protected open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val element = document.selectFirst(Evaluator.Class("de-info__box"))!!
        title = element.selectFirst(Evaluator.Class("comic-title"))!!.ownText()
        thumbnail_url = element.selectFirst(Evaluator.Tag("img"))!!.attr("src")
        author = element.selectFirst(Evaluator.Class("name"))!!.text()
        genre = element.selectFirst(Evaluator.Class("comic-status"))!!.select(Evaluator.Tag("a")).joinToString { it.ownText() }
        description = element.selectFirst(Evaluator.Class("intro-total"))!!.text()
    }

    protected open fun chapterListParse(document: Document): List<SChapter> = getDescendingChapters(
        document.select(chapterListSelector()).map {
            val link = it.child(0)
            SChapter.create().apply {
                url = link.attr("href").removePathPrefix()
                name = link.text()
            }
        },
    )

    open fun chapterListSelector() = ".chapter__list-box > li"

    open fun getDescendingChapters(chapters: List<SChapter>) = chapters.asReversed()

    override fun getChapterUrl(chapter: SChapter) = baseUrl.mobileUrl() + chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(baseUrl + chapter.url, if (config.useMobilePageList) headers else pcHeaders))

    protected open fun pageListParse(response: Response): List<Page> = config.pageListParse(response)

    // Don't send referer
    override fun imageRequest(page: Page) = super.imageRequest(page).newBuilder().headers(pcHeaders).build()

    override val supportsFilterFetching get() = config.hasCategoryPage

    override suspend fun fetchFilterData(): JsonElement = parseGenres(fetchGenresPage()).toJsonElement()

    protected open suspend fun fetchGenresPage(): Document = client.get("$baseUrl/category/", pcHeaders).asJsoup()

    override fun getFilterList(data: JsonElement?): FilterList = getWebFilters(data?.parseAs<List<Pair<String, String>>>())
}

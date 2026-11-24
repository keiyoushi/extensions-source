package eu.kanade.tachiyomi.multisrc.mccms

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import keiyoushi.utils.parseAs as parseAsRaw

/**
 * 漫城CMS http://mccms.cn/
 */
abstract class MCCMS : KeiSource() {

    protected open val config: MCCMSConfig = MCCMSConfig()

    init {
        Intl.lang = lang
    }

    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        // for thumbnail requests
        var request = chain.request()
        val referer = request.header("Referer")
        if (referer != null && !request.url.toString().startsWith(referer)) {
            request = request.newBuilder().removeHeader("Referer").build()
        }
        chain.proceed(request)
    }
        .rateLimit(2) { it.host == baseUrl.toHttpUrl().host }

    override fun Headers.Builder.configureHeaders() = set("User-Agent", System.getProperty("http.agent")!!)

    protected open fun SManga.cleanup(): SManga = this

    private fun comicListUrl(page: Int) = "$baseUrl/api/data/comic".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("size", PAGE_SIZE.toString())

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangasPage(client.get(comicListUrl(page).addQueryParameter("order", "hits").build(), Fetch.XHR))

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangasPage(client.get(comicListUrl(page).addQueryParameter("order", "addtime").build(), Fetch.XHR))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = comicListUrl(page).apply {
            val isTextSearch = query.isNotBlank()
            if (isTextSearch) addQueryParameter("key", query)
            for (filter in filters) {
                if (filter !is MCCMSFilter || (isTextSearch && filter.isTypeQuery)) continue
                val part = filter.query
                if (part.isNotEmpty()) addQueryParameter(part.substringBefore('='), part.substringAfter('='))
            }
        }.build()
        return parseMangasPage(client.get(url, Fetch.XHR))
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val list: List<MangaDto> = response.parseAs()
        return MangasPage(list.map { it.toSManga().cleanup() }, list.size >= PAGE_SIZE)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga = { if (fetchDetails) fetchMangaDetails(manga) else manga },
        chapters = { if (fetchChapters) fetchChapterList(manga) else chapters },
    )

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val url = "$baseUrl/api/data/comic".toHttpUrl().newBuilder()
            .addQueryParameter("key", manga.title)
            .build()
        val list = client.get(url, Fetch.XHR).parseAs<List<MangaDto>>()
        return list.first { it.cleanUrl == manga.url }.toSManga().cleanup()
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val id = manga.thumbnail_url!!.substringAfterLast('#', missingDelimiterValue = "").ifEmpty { throw Exception("请刷新漫画") }
        val dataUrl = "$baseUrl/api/data/chapter".toHttpUrl().newBuilder().addQueryParameter("mid", id).build()
        val dataList: List<ChapterDataDto> = client.get(dataUrl, Fetch.XHR).parseAs() // unordered
        val dateMap = HashMap<Int, Long>(dataList.size * 2)
        dataList.forEach { dateMap[it.id.toInt()] = it.date }
        val listUrl = "$baseUrl/api/comic/chapter".toHttpUrl().newBuilder().addQueryParameter("mid", id).build()
        val list: List<ChapterDto> = client.get(listUrl, Fetch.XHR).parseAs()
        return list.map { it.toSChapter(date = dateMap[it.id.toInt()] ?: 0) }.asReversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = config.pageListParse(client.get(getChapterUrl(chapter), if (config.useMobilePageList) headers else pcHeaders, Fetch.DOCUMENT))

    // Don't send referer
    override fun getImageRequest(page: Page) = super.getImageRequest(page).newBuilder().headers(pcHeaders).build()

    private inline fun <reified T> Response.parseAs(): T = parseAsRaw<ResultDto<T>>().data

    override val supportsFilterFetching get() = config.hasCategoryPage

    override suspend fun fetchFilterData(): JsonElement = parseGenres(client.get("$baseUrl/category/", pcHeaders, Fetch.DOCUMENT).asJsoup()).toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAsRaw<List<Pair<String, String>>>())
}

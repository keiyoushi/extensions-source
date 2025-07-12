package eu.kanade.tachiyomi.multisrc.mccms

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.net.URLEncoder
import keiyoushi.utils.parseAs as parseAsRaw

/**
 * 漫城CMS http://mccms.cn/
 */
open class MCCMS(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String = "zh",
    private val config: MCCMSConfig = MCCMSConfig(),
) : HttpSource() {
    override val supportsLatest get() = true

    init {
        Intl.lang = lang
    }

    override val client by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            .addInterceptor { chain -> // for thumbnail requests
                var request = chain.request()
                val referer = request.header("Referer")
                if (referer != null && !request.url.toString().startsWith(referer)) {
                    request = request.newBuilder().removeHeader("Referer").build()
                }
                chain.proceed(request)
            }
            .build()
    }

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", System.getProperty("http.agent")!!)
        .add("Referer", baseUrl)

    protected open fun SManga.cleanup(): SManga = this

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/data/comic?page=$page&size=$PAGE_SIZE&order=hits", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val list: List<MangaDto> = response.parseAs()
        return MangasPage(list.map { it.toSManga().cleanup() }, list.size >= PAGE_SIZE)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/api/data/comic?page=$page&size=$PAGE_SIZE&order=addtime", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val queries = buildList {
            add("page=$page")
            add("size=$PAGE_SIZE")
            val isTextSearch = query.isNotBlank()
            if (isTextSearch) add("key=" + URLEncoder.encode(query, "UTF-8"))
            for (filter in filters) if (filter is MCCMSFilter) {
                if (isTextSearch && filter.isTypeQuery) continue
                val part = filter.query
                if (part.isNotEmpty()) add(part)
            }
        }
        val url = buildString {
            append(baseUrl).append("/api/data/comic?")
            queries.joinTo(this, separator = "&")
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val url = "$baseUrl/api/data/comic".toHttpUrl().newBuilder()
            .addQueryParameter("key", manga.title)
            .toString()
        val mangaUrl = manga.url
        return client.newCall(GET(url, headers))
            .asObservableSuccess().map { response ->
                val list = response.parseAs<List<MangaDto>>()
                list.first { it.cleanUrl == mangaUrl }.toSManga().cleanup()
            }
    }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val id = manga.thumbnail_url!!.substringAfterLast('#', missingDelimiterValue = "").ifEmpty { throw Exception("请刷新漫画") }
        val dataResponse = client.newCall(GET("$baseUrl/api/data/chapter?mid=$id", headers)).execute()
        val dataList: List<ChapterDataDto> = dataResponse.parseAs() // unordered
        val dateMap = HashMap<Int, Long>(dataList.size * 2)
        dataList.forEach { dateMap[it.id.toInt()] = it.date }
        val response = client.newCall(GET("$baseUrl/api/comic/chapter?mid=$id", headers)).execute()
        val list: List<ChapterDto> = response.parseAs()
        val result = list.map { it.toSChapter(date = dateMap[it.id.toInt()] ?: 0) }.asReversed()
        result
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun pageListRequest(chapter: SChapter): Request =
        GET(baseUrl + chapter.url, if (config.useMobilePageList) headers else pcHeaders)

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun pageListParse(response: Response): List<Page> {
        return config.pageListParse(response)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Don't send referer
    override fun imageRequest(page: Page) = GET(page.imageUrl!!, pcHeaders)

    private inline fun <reified T> Response.parseAs(): T = parseAsRaw<ResultDto<T>>().data

    override fun getFilterList(): FilterList {
        val genreData = config.genreData.also { it.fetchGenres(this) }
        return getFilters(genreData)
    }
}

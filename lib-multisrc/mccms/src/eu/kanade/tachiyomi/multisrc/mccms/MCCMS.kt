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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.URLEncoder
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
        .removeAll("Origin")

    protected open fun SManga.cleanup(): SManga = this

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangasPage(client.get("$baseUrl/api/data/comic?page=$page&size=$PAGE_SIZE&order=hits"))

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangasPage(client.get("$baseUrl/api/data/comic?page=$page&size=$PAGE_SIZE&order=addtime"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val queries = buildList {
            add("page=$page")
            add("size=$PAGE_SIZE")
            val isTextSearch = query.isNotBlank()
            if (isTextSearch) add("key=" + URLEncoder.encode(query, "UTF-8"))
            for (filter in filters) {
                if (filter is MCCMSFilter) {
                    if (isTextSearch && filter.isTypeQuery) continue
                    val part = filter.query
                    if (part.isNotEmpty()) add(part)
                }
            }
        }
        val url = buildString {
            append(baseUrl).append("/api/data/comic?")
            queries.joinTo(this, separator = "&")
        }
        return parseMangasPage(client.get(url))
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val list: List<MangaDto> = response.parseAs()
        return MangasPage(list.map { it.toSManga().cleanup() }, list.size >= PAGE_SIZE)
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val url = "$baseUrl/api/data/comic".toHttpUrl().newBuilder()
            .addQueryParameter("key", manga.title)
            .build()
        val list = client.get(url).parseAs<List<MangaDto>>()
        return list.first { it.cleanUrl == manga.url }.toSManga().cleanup()
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val id = manga.thumbnail_url!!.substringAfterLast('#', missingDelimiterValue = "").ifEmpty { throw Exception("请刷新漫画") }
        val dataList: List<ChapterDataDto> = client.get("$baseUrl/api/data/chapter?mid=$id").parseAs() // unordered
        val dateMap = HashMap<Int, Long>(dataList.size * 2)
        dataList.forEach { dateMap[it.id.toInt()] = it.date }
        val list: List<ChapterDto> = client.get("$baseUrl/api/comic/chapter?mid=$id").parseAs()
        return list.map { it.toSChapter(date = dateMap[it.id.toInt()] ?: 0) }.asReversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = config.pageListParse(client.get(baseUrl + chapter.url, if (config.useMobilePageList) headers else pcHeaders))

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    // Don't send referer
    override fun imageRequest(page: Page) = super.imageRequest(page).newBuilder().headers(pcHeaders).build()

    private inline fun <reified T> Response.parseAs(): T = parseAsRaw<ResultDto<T>>().data

    override val supportsFilterFetching get() = config.hasCategoryPage

    override suspend fun fetchFilterData(): JsonElement = parseGenres(client.get("$baseUrl/category/", pcHeaders).asJsoup()).toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAsRaw<List<Pair<String, String>>>())
}

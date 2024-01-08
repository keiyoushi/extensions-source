package eu.kanade.tachiyomi.multisrc.mccms

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import kotlin.concurrent.thread

/**
 * 漫城CMS http://mccms.cn/
 */
open class MCCMS(
    override val name: String,
    override val baseUrl: String,
    override val lang: String = "zh",
    hasCategoryPage: Boolean = false,
) : HttpSource() {
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client by lazy {
        network.client.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            .addInterceptor(DecryptInterceptor)
            .build()
    }

    val pcHeaders by lazy { super.headersBuilder().build() }

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", System.getProperty("http.agent")!!)
        .add("Referer", baseUrl)

    protected open fun SManga.cleanup(): SManga = this
    protected open fun MangaDto.prepare(): MangaDto = this

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/data/comic?page=$page&size=$PAGE_SIZE&order=hits", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val list: List<MangaDto> = response.parseAs()
        return MangasPage(list.map { it.prepare().toSManga().cleanup() }, list.size >= PAGE_SIZE)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/api/data/comic?page=$page&size=$PAGE_SIZE&order=addtime", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val queries = buildList {
            add("page=$page")
            add("size=$PAGE_SIZE")
            val isTextSearch = query.isNotBlank()
            if (isTextSearch) add("key=$query")
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

    // preserve mangaDetailsRequest for WebView
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val url = "$baseUrl/api/data/comic".toHttpUrl().newBuilder()
            .addQueryParameter("key", manga.title)
            .toString()
        return client.newCall(GET(url, headers))
            .asObservableSuccess().map { response ->
                val list = response.parseAs<List<MangaDto>>().map { it.prepare() }
                list.find { it.url == manga.url }!!.toSManga().cleanup()
            }
    }

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException("Not used.")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val id = getMangaId(manga.url)
        val dataResponse = client.newCall(GET("$baseUrl/api/data/chapter?mid=$id", headers)).execute()
        val dataList: List<ChapterDataDto> = dataResponse.parseAs() // unordered
        val dateMap = HashMap<Int, Long>(dataList.size * 2)
        dataList.forEach { dateMap[it.id.toInt()] = it.date }
        val response = client.newCall(GET("$baseUrl/api/comic/chapter?mid=$id", headers)).execute()
        val list: List<ChapterDto> = response.parseAs()
        val result = list.map { it.toSChapter(date = dateMap[it.id.toInt()] ?: 0) }.asReversed()
        result
    }

    protected open fun getMangaId(url: String) = url.substringAfterLast('/')

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used.")

    override fun pageListRequest(chapter: SChapter): Request =
        GET(baseUrl + chapter.url, pcHeaders)

    protected open val lazyLoadImageAttr = "data-original"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img[$lazyLoadImageAttr]").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr(lazyLoadImageAttr))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used.")

    override fun imageRequest(page: Page) = GET(page.imageUrl!!, pcHeaders)

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream<ResultDto<T>>(it.body.byteStream()).data
    }

    val genreData = GenreData(hasCategoryPage)

    fun fetchGenres() {
        if (genreData.status != GenreData.NOT_FETCHED) return
        genreData.status = GenreData.FETCHING
        thread {
            try {
                val response = client.newCall(GET("$baseUrl/category/", pcHeaders)).execute()
                parseGenres(response.asJsoup(), genreData)
            } catch (e: Exception) {
                genreData.status = GenreData.NOT_FETCHED
                Log.e("MCCMS/$name", "failed to fetch genres", e)
            }
        }
    }

    override fun getFilterList(): FilterList {
        fetchGenres()
        return getFilters(genreData)
    }
}

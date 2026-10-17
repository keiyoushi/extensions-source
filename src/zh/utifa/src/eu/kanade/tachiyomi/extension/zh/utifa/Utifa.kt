package eu.kanade.tachiyomi.extension.zh.utifa

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class Utifa : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    override val supportsFilterFetching = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::resignExpiredPage)

    override suspend fun getPopularManga(page: Int): MangasPage = mangaList(page, orderBy = "views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaList(page, orderBy = "updateTime")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        val categories = filters.filterIsInstance<CategoryFilter>().associate { it.key to it.value }
        return mangaList(
            page,
            keyword = query.trim().ifEmpty { null },
            orderBy = sort?.value ?: "views",
            order = sort?.order ?: "desc",
            theme = categories["theme"],
            grade = categories["grade"],
            updateStatus = categories["updateStatus"],
        )
    }

    private suspend fun mangaList(
        page: Int,
        keyword: String? = null,
        orderBy: String,
        order: String = "desc",
        theme: String? = null,
        grade: String? = null,
        updateStatus: String? = null,
    ): MangasPage {
        val body = MangaListBody(page, PAGE_SIZE, keyword, orderBy, order, theme, grade, updateStatus)
        val data = client.post("$apiUrl/manga/common/list", body = body.toJsonRequestBody()).parseData<MangaPageDto>()
        return MangasPage(data.records.map { it.toSManga(apiUrl) }, data.current < data.pages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.size != 3 || segments[0] != "manga" || segments[1] != "detail") return null
        val id = segments[2].toLongOrNull() ?: return null
        val manga = SManga.create().apply { this.url = id.toString() }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$apiUrl/manga/common/get".toHttpUrl().newBuilder()
            .addQueryParameter("id", manga.url)
            .build()
        val data = client.get(url).parseData<MangaDto>()
        return SMangaUpdate(data.toSManga(apiUrl), data.chapters.map { it.toSChapter() }.reversed())
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/detail/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/chapter/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(chapterPagesUrl(chapter.url)).parseData<ChapterPagesDto>()
        .chapters.mapIndexed { index, file -> Page(index, imageUrl = "$apiUrl${file.url}#${chapter.url}") }

    private fun chapterPagesUrl(chapterId: String) = "$apiUrl/manga/common/getChapterList".toHttpUrl().newBuilder()
        .addQueryParameter("chapterId", chapterId)
        .build()

    // Page links are signed for 30 minutes; the fragment holds the chapter id
    private fun resignExpiredPage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val chapterId = request.url.fragment ?: return chain.proceed(request)
        val response = chain.proceed(request)
        if (response.code != 403) return response
        response.close()

        val path = request.url.encodedPath
        val url = resignedPages(chapterId).firstOrNull { it.encodedPath == path }
            ?: throw IOException("Page not found in chapter $chapterId")
        return chain.proceed(request.newBuilder().url(url).build())
    }

    // Last re-signed chapter, so its other expired pages need no further request
    private var resigned: ResignedPages? = null

    @Synchronized
    private fun resignedPages(chapterId: String): List<HttpUrl> {
        val now = System.currentTimeMillis()
        resigned?.let { if (it.chapterId == chapterId && now - it.fetchedAt < RESIGNED_TTL) return it.urls }
        val urls = try {
            val response = client.newCall(GET(chapterPagesUrl(chapterId), headers)).execute()
            if (!response.isSuccessful) {
                response.close()
                throw IOException("HTTP error ${response.code}")
            }
            response.parseData<ChapterPagesDto>().chapters.map { "$apiUrl${it.url}".toHttpUrl() }
        } catch (e: Exception) {
            throw e as? IOException ?: IOException(e)
        }
        resigned = ResignedPages(chapterId, now, urls)
        return urls
    }

    private class ResignedPages(val chapterId: String, val fetchedAt: Long, val urls: List<HttpUrl>)

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/manga/common/categoryTree").parseData()

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<CategoryDto>>() ?: return FilterList()
        return FilterList(
            categories.map {
                if (it.key == "orderBy") SortFilter(it.label, it.value) else CategoryFilter(it.key, it.label, it.value)
            },
        )
    }

    private inline fun <reified T> Response.parseData(): T = parseAs<ApiResponse<T>>().let { it.data ?: throw Exception(it.message) }

    companion object {
        private const val PAGE_SIZE = 24
        private const val RESIGNED_TTL = 25 * 60 * 1000L
    }
}

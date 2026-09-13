package eu.kanade.tachiyomi.extension.en.webnovel

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.Calendar
import java.util.Date

@Source
abstract class WebNovel : KeiSource() {

    private val baseApiUrl = "$baseUrl$BASE_API_ENDPOINT"

    private val baseCoverURl = baseUrl.replace("www", "book-pic")

    private val baseCdnUrl = baseUrl.replace("www", "comic-image")

    override fun OkHttpClient.Builder.configureClient() = apply {
        addNetworkInterceptor(::csrfTokenInterceptor)
        addInterceptor(::expiredImageUrlInterceptor)
    }

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortByFilter(default = 1)))

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortByFilter(default = 5)))

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseApiUrl$QUERY_SEARCH_PATH?type=manga&pageIndex=$page".toHttpUrl()
                .newBuilder()
                .addQueryParameter("keywords", query)
                .toString()
        } else {
            val sort = filters.firstInstanceOrNull<SortByFilter>()?.selectedValue.orEmpty()
            val contentStatus = filters.firstInstanceOrNull<ContentStatusFilter>()?.selectedValue.orEmpty()
            val genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedValue.orEmpty()

            "$baseApiUrl$FILTER_SEARCH_PATH?categoryType=2&pageIndex=$page" +
                "&categoryId=$genre&bookStatus=$contentStatus&orderBy=$sort"
        }

        val response = client.get(url)

        return if (url.contains(QUERY_SEARCH_PATH)) {
            response.parseAsForWebNovel<QuerySearchResponse>().toMangasPage(::getCoverUrl)
        } else {
            response.parseAsForWebNovel<FilterSearchResponse>().toMangasPage(::getCoverUrl)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "comic") {
            return null
        }

        val manga = SManga.create().apply {
            this.url = url.pathSegments[1].substringAfterLast("_")
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
            }
    }

    // Filters
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        ContentStatusFilter(),
        SortByFilter(),
        GenreFilter(),
    )

    // Manga updates
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.getId}"

    override fun getChapterUrl(chapter: SChapter): String {
        val (comicId, chapterId) = chapter.getMangaAndChapterId
        return "$baseUrl/comic/$comicId/$chapterId"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val (manga, chapters) = coroutineScope {
            val mangaD = async { if (fetchDetails) getMangaDetails(manga) else manga }
            val chaptersD = async { if (fetchChapters) getChapterList(manga) else chapters }
            mangaD.await() to chaptersD.await()
        }

        return SMangaUpdate(manga, chapters)
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val response = client.get("$baseApiUrl/comic/getComicDetailPage?comicId=${manga.getId}")
        return response.parseAsForWebNovel<ComicDetailInfoResponse>().toSManga(::getCoverUrl)
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val chapterList = client.get("$baseApiUrl/comic/getChapterList?comicId=${manga.getId}")
            .parseAsForWebNovel<ComicChapterListResponse>()

        val comic = chapterList.comic
        val chapters = chapterList.chapters.reversed().asSequence()

        val accurateUpdateTimes = runCatching {
            client.get("$WEBNOVEL_UPLOAD_TIME/${comic.id}.json").parseAs<Map<String, Long>>()
        }
            .getOrDefault(emptyMap())

        val updateTimes = chapters.map { accurateUpdateTimes[it.id] ?: it.publishTime.toDate() }

        val filteredChapters = chapters.filter { it.isVisible }

        // When new privileged chapter is released oldest privileged chapter becomes normal one (in most cases)
        // but since those normal chapter retain the original upload time we improvise. (This isn't optimal but meh)
        return filteredChapters.zip(updateTimes) { chapter, updateTime ->
            SChapter.create().apply {
                name = if (chapter.isLocked) "\uD83D\uDD12 ${chapter.name}" else chapter.name
                url = "${comic.id}:${chapter.id}"
                date_upload = updateTime
            }
        }.toList()
    }

    private fun String.toDate(): Long {
        if (contains("now", ignoreCase = true)) return Date().time

        val number = DIGIT_REGEX.find(this)?.value?.toIntOrNull() ?: return 0
        val field = when {
            contains("year") -> Calendar.YEAR
            contains("month") -> Calendar.MONTH
            contains("day") -> Calendar.DAY_OF_MONTH
            contains("hour") -> Calendar.HOUR
            contains("minute") -> Calendar.MINUTE
            else -> return 0
        }

        return Calendar.getInstance().apply { add(field, -number) }.timeInMillis
    }

    // Pages
    data class ChapterPage(
        val id: String,
        val url: String,
    )

    // LinkedHashMap with a capacity of 25. When exceeding the capacity the oldest entry is removed.
    private val chapterPageCache = object : LinkedHashMap<String, List<ChapterPage>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ChapterPage>>?): Boolean = size > 25
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (comicId, chapterId) = chapter.getMangaAndChapterId
        val response = client.get(pageListUrl(comicId, chapterId))
        return parsePageList(response)
    }

    private fun pageListUrl(comicId: String, chapterId: String): String {
        // Given a high [width] value WebNovel returns the highest resolution image publicly available
        return "$baseApiUrl/comic/getContent?comicId=$comicId&chapterId=$chapterId&width=9999"
    }

    private fun parsePageList(response: Response): List<Page> {
        val chapterContent = response.parseAsForWebNovel<ChapterContentResponse>().data
        return chapterContent.pages.map { ChapterPage(it.id, it.url) }
            .also { chapterPageCache[chapterContent.id.toString()] = it }
            .mapIndexed { i, chapterPage -> Page(i, imageUrl = chapterPage.url) }
    }

    // Utilities
    private val SManga.getId: String
        get() {
            url.toLongOrNull() ?: throw Exception(MIGRATE_MESSAGE)
            return url
        }

    private val SChapter.getMangaAndChapterId: Pair<String, String>
        get() {
            val (comicId, chapterId) = url.split(":")
            if (listOf(comicId, chapterId).any { it.toLongOrNull() == null }) throw Exception(MIGRATE_MESSAGE)
            return comicId to chapterId
        }

    private fun getCoverUrl(comicId: String, coverUpdatedAt: Long): String = "$baseCoverURl/bookcover/$comicId?imageId=$coverUpdatedAt&imageMogr2/thumbnail/1024x"

    private fun csrfTokenInterceptor(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalRequestUrl = originalRequest.url
        if (!originalRequestUrl.toString().contains(BASE_API_ENDPOINT)) return chain.proceed(originalRequest)

        val csrfToken = originalRequest.header("cookie")
            ?.takeIf { CSRF_TOKEN_NAME in it }
            ?.substringAfter("$CSRF_TOKEN_NAME=")
            ?.substringBefore(";")
            ?: throw IOException("Open in WebView to set necessary cookies.")

        val newUrl = originalRequestUrl.newBuilder()
            .addQueryParameter(CSRF_TOKEN_NAME, csrfToken)
            .build()

        val newRequest = originalRequest.newBuilder().url(newUrl).build()
        return chain.proceed(newRequest)
    }

    private fun expiredImageUrlInterceptor(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalRequestUrl = originalRequest.url

        // If original request is not a page url or the url is still valid we just continue with og request
        if (!originalRequestUrl.toString().contains(baseCdnUrl) || isPageUrlStillValid(originalRequestUrl)) {
            return chain.proceed(originalRequest)
        }

        val (_, comicId, chapterId, pageFileName) = originalRequest.url.pathSegments

        // Page url is not valid anymore so we check if cache has updated one
        val pageId = pageFileName.substringBefore("!")
        val cachedPageUrl = chapterPageCache[chapterId]?.firstOrNull { it.id == pageId }?.url
        if (cachedPageUrl != null && isPageUrlStillValid(cachedPageUrl.toHttpUrl())) return chain.proceed(originalRequest)

        // Time to get it from site
        parsePageList(chain.proceed(GET(pageListUrl(comicId, chapterId))))

        val newPageUrl = chapterPageCache[chapterId]?.firstOrNull { it.id == pageId }?.url?.toHttpUrl()
            ?: throw IOException("Couldn't regenerate expired image url")

        val newRequest = originalRequest.newBuilder().url(newPageUrl).build()
        return chain.proceed(newRequest)
    }

    private fun isPageUrlStillValid(imageUrl: HttpUrl): Boolean {
        val urlGenerationTime = imageUrl.queryParameter("t")?.toLongOrNull()?.times(1000)
            ?: throw IOException("Couldn't get image generation time from page url")

        // Urls are valid for 10 minutes after generation. We check for 9min and 30s just to be safe
        return Date().time - urlGenerationTime <= 570000
    }

    private inline fun <reified T> Response.parseAsForWebNovel(): T {
        val parsed = parseAs<ResponseWrapper<T>>()
        if (parsed.code != 0) error("Error ${parsed.code}: ${parsed.msg}")
        return requireNotNull(parsed.data) { "Received response data was null" }
    }

    companion object {
        private const val BASE_API_ENDPOINT = "/go/pcm"

        private const val QUERY_SEARCH_PATH = "/search/result"
        private const val FILTER_SEARCH_PATH = "/category/categoryAjax"

        private const val MIGRATE_MESSAGE = "Migrate this entry from \"Webnovel.com\" to \"Webnovel.com\" to update its URL"

        private val DIGIT_REGEX = "(\\d+)".toRegex()

        private const val CSRF_TOKEN_NAME = "_csrfToken"

        private const val WEBNOVEL_UPLOAD_TIME = "https://keiyoushi.github.io/webnovel-upload-time"
    }
}

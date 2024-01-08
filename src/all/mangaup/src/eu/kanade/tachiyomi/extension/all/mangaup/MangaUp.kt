package eu.kanade.tachiyomi.extension.all.mangaup

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class MangaUp(override val lang: String) : HttpSource() {

    override val name = "Manga UP!"

    override val baseUrl = "https://global.manga-up.com"

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", baseUrl)
        .add("User-Agent", USER_AGENT)

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::thumbnailIntercept)
        .rateLimitHost(API_URL.toHttpUrl(), 1)
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    private val json: Json by injectLazy()

    private var titleList: List<MangaUpTitle>? = null

    override fun popularMangaRequest(page: Int): Request {
        return GET("$API_URL/search?format=json", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        titleList = response.parseAs<MangaUpSearch>().titles

        val titles = titleList!!
            .sortedByDescending { it.bookmarkCount ?: 0 }
            .map(MangaUpTitle::toSManga)

        return MangasPage(titles, hasNextPage = false)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(PREFIX_ID_SEARCH) && query.matches(ID_SEARCH_PATTERN)) {
            return mangaDetailsRequest(query.removePrefix(PREFIX_ID_SEARCH))
        }

        val apiUrl = "$API_URL/manga/search".toHttpUrl().newBuilder()
            .addQueryParameter("word", query)
            .addQueryParameter("format", "json")
            .toString()

        return GET(apiUrl, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.toString().contains("manga/detail")) {
            val titleId = response.request.url.queryParameter("title_id")!!

            val title = response.parseAs<MangaUpTitle>().toSManga().apply {
                url = "/manga/$titleId"
            }

            return MangasPage(listOf(title), hasNextPage = false)
        }

        val titles = response.parseAs<MangaUpSearch>().titles

        val query = response.request.url.queryParameter("word")

        if (query.isNullOrEmpty()) {
            fetchAllTitles()
        } else {
            titleList = titles
        }

        return MangasPage(titles.map(MangaUpTitle::toSManga), hasNextPage = false)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request = mangaDetailsRequest(manga.url)

    private fun mangaDetailsRequest(mangaUrl: String): Request {
        val titleId = mangaUrl.substringAfterLast("/")

        val apiUrl = "$API_URL/manga/detail".toHttpUrl().newBuilder()
            .addQueryParameter("title_id", titleId)
            .addQueryParameter("ui_lang", lang)
            .addQueryParameter("format", "json")
            .toString()

        return GET(apiUrl, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<MangaUpTitle>().toSManga()
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga.url)

    override fun chapterListParse(response: Response): List<SChapter> {
        val titleId = response.request.url.queryParameter("title_id")!!.toInt()

        return response.parseAs<MangaUpTitle>().readableChapters
            .map { it.toSChapter(titleId) }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")

        return GET("$API_URL/manga/viewer?chapter_id=$chapterId&format=json", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<MangaUpViewer>().pages
            .mapIndexed { i, page -> Page(i, "", page.imageUrl) }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    // Fetch all titles to get newer thumbnail URLs in the interceptor.
    private fun fetchAllTitles() = runCatching {
        val popularResponse = client.newCall(popularMangaRequest(1)).execute()
        titleList = popularResponse.parseAs<MangaUpSearch>().titles
    }

    private fun thumbnailIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 410 && request.url.toString().contains(TITLE_THUMBNAIL_PATH)) {
            val titleId = request.url.toString()
                .substringAfter("/$TITLE_THUMBNAIL_PATH/")
                .substringBefore(".webp")
                .toInt()
            val title = titleList?.find { it.id == titleId } ?: return response

            val thumbnailUrl = title.mainThumbnailUrl
                ?: title.thumbnailUrl
                ?: return response

            response.close()
            val thumbnailRequest = GET(thumbnailUrl, request.headers)
            return chain.proceed(thumbnailRequest)
        }

        return response
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(body.string())
    }

    companion object {
        private const val API_URL = "https://global-web-api.manga-up.com/api"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36"

        private const val TITLE_THUMBNAIL_PATH = "manga_list"

        const val PREFIX_ID_SEARCH = "id:"
        private val ID_SEARCH_PATTERN = "^id:(\\d+)$".toRegex()
    }
}

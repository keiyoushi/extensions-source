package eu.kanade.tachiyomi.extension.all.mangapluscreators

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class MangaPlusCreators(override val lang: String) : HttpSource() {

    override val name = "MANGA Plus Creators by SHUEISHA"

    override val baseUrl = "https://medibang.com/mpc"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", baseUrl.substringBeforeLast("/"))
        .add("Referer", baseUrl)
        .add("User-Agent", USER_AGENT)

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/titles/popular/?p=m")
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val apiUrl = "$API_URL/titles/popular/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("pageSize", POPULAR_PAGE_SIZE)
            .addQueryParameter("l", lang)
            .addQueryParameter("p", "m")
            .addQueryParameter("isWebview", "false")
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .toString()

        return GET(apiUrl, newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asMpcResponse()

        checkNotNull(result.titles) { EMPTY_RESPONSE_ERROR }

        val titles = result.titles.titleList.orEmpty().map(MpcTitle::toSManga)

        return MangasPage(titles, result.titles.pagination?.hasNextPage ?: false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/titles/recent/?t=episode")
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val apiUrl = "$API_URL/titles/recent/list".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("pageSize", POPULAR_PAGE_SIZE)
            .addQueryParameter("l", lang)
            .addQueryParameter("c", "episode")
            .addQueryParameter("isWebview", "false")
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .toString()

        return GET(apiUrl, newHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val refererUrl = "$baseUrl/keywords".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .toString()

        val newHeaders = headersBuilder()
            .set("Referer", refererUrl)
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val apiUrl = "$API_URL/search/titles".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("pageSize", POPULAR_PAGE_SIZE)
            .addQueryParameter("sort", "newly")
            .addQueryParameter("lang", lang)
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .toString()

        return GET(apiUrl, newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.asJsoup()
        val bookBox = result.selectFirst(".book-box")!!

        return SManga.create().apply {
            title = bookBox.selectFirst("div.title")!!.text()
            author = bookBox.selectFirst("div.mod-btn-profile div.name")!!.text()
            description = bookBox.select("div.summary p")
                .joinToString("\n\n") { it.text() }
            status = when (bookBox.selectFirst("div.book-submit-type")!!.text()) {
                "Series" -> SManga.ONGOING
                "One-shot" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = bookBox.select("div.genre-area div.tag-genre")
                .joinToString { it.text() }
            thumbnail_url = bookBox.selectFirst("div.cover img")!!.attr("data-src")
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val titleId = manga.url.substringAfterLast("/")

        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + manga.url)
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val apiUrl = "$API_URL/titles/$titleId/episodes/".toHttpUrl().newBuilder()
            .addQueryParameter("page", "1")
            .addQueryParameter("pageSize", CHAPTER_PAGE_SIZE)
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .toString()

        return GET(apiUrl, newHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.asMpcResponse()

        checkNotNull(result.episodes) { EMPTY_RESPONSE_ERROR }

        return result.episodes.episodeList.orEmpty()
            .sortedByDescending(MpcEpisode::numbering)
            .map(MpcEpisode::toSChapter)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")

        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + chapter.url)
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val apiUrl = "$API_URL/episodes/pageList/$chapterId/".toHttpUrl().newBuilder()
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .toString()

        return GET(apiUrl, newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.asMpcResponse()

        checkNotNull(result.pageList) { EMPTY_RESPONSE_ERROR }

        val referer = response.request.header("Referer")!!

        return result.pageList.mapIndexed { i, page ->
            Page(i, referer, page.publicBgImage)
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .removeAll("Origin")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun Response.asMpcResponse(): MpcResponse = use {
        json.decodeFromString(body.string())
    }

    companion object {
        private const val API_URL = "https://medibang.com/api/mpc"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36"

        private const val POPULAR_PAGE_SIZE = "30"
        private const val CHAPTER_PAGE_SIZE = "200"

        private const val EMPTY_RESPONSE_ERROR = "Empty response from the API. Try again later."
    }
}

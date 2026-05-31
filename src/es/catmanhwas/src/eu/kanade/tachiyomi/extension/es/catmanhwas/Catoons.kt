package eu.kanade.tachiyomi.extension.es.catmanhwas

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.applicationContext
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.map

class Catoons : HttpSource() {

    override val name = "Catoons"

    override val baseUrl = "https://newcat1.xyz"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(3, 1)
        .build()

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList(OrderFilter(listOf("" to "popular"))))

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList(OrderFilter(listOf("" to "recent"))))

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series/__data.json".toHttpUrl().newBuilder()

        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> url.addQueryParameter("genre", filter.selected)
                is OrderFilter -> url.addQueryParameter("sort", filter.selected)
                else -> {}
            }
        }

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        url.addQueryParameter("x-sveltekit-invalidated", "001")

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dataNode = response.parseAs<SvelteDataDto>().getDataNode()
        val data = decodeSvelte(dataNode).parseAs<BrowseDto>()
        return MangasPage(data.series.map { it.toSManga() }, data.hasNextPage())
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaSlug = response.request.url.pathSegments.last()
        getRemoteChunks("$baseUrl/series/$mangaSlug")
        val details = getDetailsFromApi(mangaSlug)
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.font-bold")!!.text()
            thumbnail_url = document.selectFirst("div.mx-auto > div > img.object-cover")?.attr("abs:src")
            description = document.selectFirst("p.leading-relaxed")?.text()
            status = details.getStatus()
            genre = details.getGenres()
        }
    }

    private fun getDetailsFromApi(slug: String): DetailsDto {
        val url = "$baseUrl/_app/remote/$detailsChunk/getSerieDetails".toHttpUrl().newBuilder()
            .addQueryParameter("payload", """["$slug"]""".toBase64())
            .build()

        val result = client.newCall(GET(url, headers)).execute().parseAs<SvelteResultDto>().getResult()
        return decodeSvelte(result.jsonArray).parseAs<DetailsDto>()
    }

    override fun chapterListRequest(manga: SManga): Request {
        getRemoteChunks("$baseUrl/series/${manga.url}")
        return paginatedChapterListRequest(manga.url, 1)
    }

    private fun paginatedChapterListRequest(slug: String, page: Int): Request {
        val url = "$baseUrl/_app/remote/$chaptersChunk/getChapters".toHttpUrl().newBuilder()
            .addQueryParameter("payload", getChapterPayload(slug, page))
            .fragment(slug)

        return GET(url.build(), headers)
    }

    private fun getChapterPayload(slug: String, page: Int): String {
        val payload = """[["__skrao",1],{"page":2,"slug":3,"perPage":4},$page,"$slug",$CHAPTERS_PER_PAGE]"""
        return payload.toBase64()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaSlug = response.request.url.fragment!!
        val result = response.parseAs<SvelteResultDto>().getResult()
        var chapterListData = decodeSvelte(result.jsonArray).parseAs<ChapterDataDto>()

        val chapterList = chapterListData.data.map { it.toSChapter(mangaSlug) }.toMutableList()

        while (chapterListData.pagination.hasNextPage()) {
            val nextPageRequest = paginatedChapterListRequest(mangaSlug, chapterListData.pagination.currentPage + 1)
            val nextPageResponse = client.newCall(nextPageRequest).execute()
            val nextPageResult = nextPageResponse.parseAs<SvelteResultDto>().getResult()
            chapterListData = decodeSvelte(nextPageResult.jsonArray).parseAs<ChapterDataDto>()
            chapterList.addAll(chapterListData.data.map { it.toSChapter(mangaSlug) })
        }

        return chapterList
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/series/${chapter.url}"

    override fun pageListRequest(chapter: SChapter) = GET("$baseUrl/series/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.items-center > div.w-full > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:src"))
        }
    }

    override fun getFilterList() = getFilters()

    private fun String.toBase64() = Base64.encodeToString(this.toByteArray(), Base64.DEFAULT)

    @Volatile
    private var detailsChunk: String? = null

    @Volatile
    private var chaptersChunk: String? = null

    @Synchronized
    private fun getRemoteChunks(seriesUrl: String) {
        if (detailsChunk != null && chaptersChunk != null) {
            return
        }

        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())

        var webView: WebView? = null

        handler.post {
            webView = WebView(applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.blockNetworkImage = true

                webViewClient = object : WebViewClient() {

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        val url = request.url.toString()

                        DETAILS_CHUNK_REGEX
                            .find(url)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.let { chunk ->
                                detailsChunk = chunk
                            }

                        CHAPTERS_CHUNK_REGEX
                            .find(url)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.let { chunk ->
                                chaptersChunk = chunk
                            }

                        if (detailsChunk != null && chaptersChunk != null) {
                            latch.countDown()
                        }

                        return super.shouldInterceptRequest(view, request)
                    }
                }

                loadUrl(seriesUrl)
            }
        }

        latch.await(20, TimeUnit.SECONDS)

        handler.post {
            webView?.destroy()
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    fun decodeSvelte(data: JsonArray): JsonElement = resolve(data, data[0])

    private fun dereference(data: JsonArray, index: Int): JsonElement = when (val value = data[index]) {
        is JsonArray, is JsonObject -> resolve(data, value)
        else -> value
    }

    private fun resolveReference(data: JsonArray, element: JsonElement): JsonElement {
        val index = (element as? JsonPrimitive)?.intOrNull

        return if (
            index != null &&
            !element.isString &&
            index in data.indices
        ) {
            dereference(data, index)
        } else {
            resolve(data, element)
        }
    }

    private fun resolve(data: JsonArray, element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map { resolveReference(data, it) })
        is JsonObject -> buildJsonObject {
            element.forEach { (key, value) ->
                put(key, resolveReference(data, value))
            }
        }
        else -> element
    }

    companion object {
        private val DETAILS_CHUNK_REGEX = """/_app/remote/([^/]+)/getSerieDetails""".toRegex()
        private val CHAPTERS_CHUNK_REGEX = """/_app/remote/([^/]+)/getChapters""".toRegex()
        private const val CHAPTERS_PER_PAGE = 100
    }
}

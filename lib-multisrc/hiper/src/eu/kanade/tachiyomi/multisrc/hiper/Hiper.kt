package eu.kanade.tachiyomi.multisrc.hiper

import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.applicationContext
import keiyoushi.utils.get
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class Hiper : HttpSource() {

    protected open val mangaPath: String = "manga"

    override val supportsLatest: Boolean = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val acceptHeaders = headersBuilder()
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .build()

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private var wvHeaders: Headers? = null

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            var request = chain.request()

            wvHeaders?.let {
                request = request.newBuilder()
                    .headers(it)
                    .build()
            }

            val response = chain.proceed(request)

            // Fetch baseUrl with accept headers which then populates a cookie
            if (response.code == 401) {
                response.close()
                network.client.newCall(GET(baseUrl, acceptHeaders)).execute().close()
                chain.proceed(chain.request())
            } else {
                response
            }
        }
        .build()

    // ============================ Popular ====================================

    private val popularFilter = FilterList(OrderByFilter("", arrayOf("" to "popular")))

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", popularFilter)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================ Latest ====================================
    private val latestFilter = FilterList(OrderByFilter("", arrayOf("" to "newest")))

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", latestFilter)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================ Search ====================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val limit = 30
        val input = buildJsonObject {
            putJsonObject("0") {
                putJsonObject("json") {
                    put("q", query)
                    filters.filterIsInstance<OrderByFilter>().forEach { filter ->
                        put("sort", filter.selected())
                    }
                    putJsonObject("filters") {
                        put("genres", null)
                        put("type", null)
                        put("status", null)
                        put("contentRating", null)
                        put("author", null)
                        put("artist", null)
                        put("year", null)
                    }

                    put("limit", limit)
                    put("offset", (page - 1) * limit)
                    put("maxRating", "pornographic")
                }
                putJsonObject("meta") {
                    putJsonObject("values") {
                        put("filters.genres", buildJsonArray { add("undefined") })
                        put("filters.type", buildJsonArray { add("undefined") })
                        put("filters.status", buildJsonArray { add("undefined") })
                        put("filters.contentRating", buildJsonArray { add("undefined") })
                        put("filters.author", buildJsonArray { add("undefined") })
                        put("filters.artist", buildJsonArray { add("undefined") })
                        put("filters.year", buildJsonArray { add("undefined") })
                    }
                }
            }
        }.toString()

        val url = "$baseUrl/api/trpc/search.query".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val element = response.parseAs<List<JsonElement>>().first()
        val dto = element["result"]["data"]["json"]?.parseAs<WrapperContent>() ?: return MangasPage(emptyList(), false)
        return MangasPage(dto.hits.map { it.toSManga(mangaPath) }, dto.hits.isNotEmpty())
    }

    // ============================ Details ===================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url
            .substringAfterLast("$mangaPath/")
            .substringBefore("#")
            .takeIf(String::isNotBlank)
            ?: throw IOException("Migrate from $name to $name")

        val input = buildJsonObject {
            putJsonObject("0") {
                put("json", null)
                putJsonObject("meta") {
                    putJsonArray("values") {
                        add("undefined")
                    }
                }
            }
            putJsonObject("1") {
                putJsonObject("json") {
                    put("slug", slug)
                }
            }
        }

        val url = "$baseUrl/api/trpc/auth.me,series.bySlugWithGenres".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val element = response.parseAs<List<JsonElement>>().last()
        return element["result"]["data"]["json"]!!.parseAs<MangaDto>().toSManga(mangaPath)
    }

    // ============================ Chapters ==================================

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBefore('#') + "/" + chapter.url.substringAfterLast('/')

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("#").toLongOrNull()
            ?: throw IOException("Migrate from $name to $name")
        val input = buildJsonObject {
            putJsonObject("0") {
                putJsonObject("json") {
                    putJsonArray("values") {
                        add("undefined")
                    }
                }
            }

            putJsonObject("1") {
                putJsonObject("json") {
                    put("seriesId", mangaId)
                    put("chapterId", null)
                    put("sort", "best")
                    put("page", 1)
                    put("limit", 20)
                }
                putJsonObject("meta") {
                    putJsonObject("values") {
                        put("chapterId", buildJsonArray { add("undefined") })
                    }
                }
            }

            putJsonObject("2") {
                putJsonObject("json") {
                    put("seriesId", mangaId)
                }
            }
        }

        val url = "$baseUrl/api/trpc/auth.me,comments.list,series.chapters".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .fragment(manga.url)
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaPath = response.request.url.fragment!!
        val element = response.parseAs<List<JsonElement>>().last()
        val chaptersDTO = element["result"]["data"]["json"]!!.parseAs<List<ChapterDto>>()
        return chaptersDTO.map { it.toSChapter(mangaPath) }
    }

    // ============================ Pages =====================================

    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url
            .substringAfterLast("$mangaPath/")
            .substringBefore("#")
            .takeIf(String::isNotBlank)
            ?: throw IOException("Migrate from $name to $name")

        val input = buildJsonObject {
            putJsonObject("0") {
                put("json", null)
                putJsonObject("meta") {
                    putJsonArray("values") {
                        add("undefined")
                    }
                }
            }
            putJsonObject("1") {
                putJsonObject("json") {
                    put("slug", slug)
                }
            }
            putJsonObject("2") {
                putJsonObject("json") {
                    put("seriesSlug", slug)
                    put("chapterNumber", chapter.chapter_number)
                }
            }
            putJsonObject("3") {
                putJsonObject("json") {
                    put("position", "footer_bottom")
                }
            }
        }

        val url = "$baseUrl/api/trpc/auth.me,series.bySlug,reader.chapterPages".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .build()

        return GET(url, headers)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val chapterUrl = getChapterUrl(chapter)

        val request = pageListRequest(chapter)
        val response = client.newCall(request).execute()
        val resJson = parsePages(response)

        resJson?.takeIf { it.isNotEmpty() } ?: run {
            fetchHeadersWv(chapterUrl)

            wvHeaders?.let {
                val response = client.newCall(request).execute()
                parsePages(response) ?: emptyList()
            } ?: error("Failed to get headers")
        }
    }

    private fun parsePages(response: Response): List<Page>? {
        val elements = response.parseAs<List<JsonElement>>()

        for (element in elements) {
            if ("error" in element.jsonObject) {
                return null
            }
        }

        val lastElement = elements.last()
        val pages = lastElement["result"]["data"]["json"]?.parseAs<List<PageDto>>() ?: return emptyList()
        return pages.map(PageDto::toPage)
    }

    @Synchronized
    fun fetchHeadersWv(url: String) {
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        val doc = client.newCall(GET(url, headers)).execute().asJsoup()

        handler.post {
            val wv = WebView(applicationContext).also { webView = it }

            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.loadsImagesAutomatically = false
            wv.settings.blockNetworkImage = true
            wv.settings.userAgentString = headers["user-agent"]

            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    if (url.startsWith("$baseUrl/api")) {
                        val allHeaders = request.requestHeaders
                        if (allHeaders.isNotEmpty()) {
                            wvHeaders = Headers.Builder().apply {
                                allHeaders.forEach { (key, value) ->
                                    if (headers.get(key) != null) {
                                        add(key, value)
                                    }
                                }
                            }.build()
                        }
                        latch.countDown()
                        return null
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            wv.loadDataWithBaseURL(doc.location(), doc.outerHtml(), "text/html", "utf-8", null)
        }

        latch.await(15, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================ Filters ====================================

    open class OrderByFilter(displayName: String, private val vals: Array<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun selected() = vals[state].second
    }
}

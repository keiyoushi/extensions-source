package eu.kanade.tachiyomi.extension.all.yabai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder

class Yabai : HttpSource() {
    override val name = "Yabai"

    override val baseUrl = "https://yabai.si"

    override val lang = "all"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::tokenInterceptor)
        .build()

    private val popularCursors = mutableMapOf<Int, String>()
    private val searchCursors = mutableMapOf<Int, String>()

    private var inertiaVersion: String? = null
    private var xsrfToken: String? = null

    private fun tokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Only process requests marked for Inertia.
        if (request.header("Inertia-Req") == null) {
            return chain.proceed(request)
        }

        if (inertiaVersion == null || xsrfToken == null) {
            updateTokens()
        }

        var response = proceedWithTokens(chain, request)

        // 409 = Inertia Version out of date, 419 = CSRF Token expired, 403 = DDOS-Guard
        if (response.code == 409 || response.code == 419 || response.code == 403) {
            response.close()
            updateTokens()
            response = proceedWithTokens(chain, request)
        }

        return response
    }

    private fun proceedWithTokens(chain: Interceptor.Chain, request: Request): Response {
        val builder = request.newBuilder()
            .removeHeader("Inertia-Req")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("X-Inertia", "true")

        inertiaVersion?.let {
            builder.addHeader("X-Inertia-Version", it)
        }

        if (request.method == "POST" || request.method == "PUT") {
            builder.addHeader("Content-Type", "application/json")
            xsrfToken?.let {
                builder.addHeader("X-XSRF-TOKEN", it)
            }
        }

        return chain.proceed(builder.build())
    }

    @Synchronized
    private fun updateTokens() {
        // We do a normal GET to baseUrl (without X-Requested-With) so that cloudflareClient
        // can successfully handle the DDOS-Guard challenge via WebView if needed.
        val request = GET(baseUrl, headers)
        val response = client.newCall(request).execute()
        val requestUrl = response.request.url

        // Extract CSRF token and URL Decode it (Fixes Laravel 419 mismatch)
        val cookie = client.cookieJar.loadForRequest(requestUrl).firstOrNull { it.name == "XSRF-TOKEN" }
        if (cookie != null) {
            xsrfToken = URLDecoder.decode(cookie.value, "UTF-8")
        } else {
            val cookies = response.headers("Set-Cookie")
            for (c in cookies) {
                if (c.startsWith("XSRF-TOKEN=")) {
                    val value = c.substringAfter("=").substringBefore(";")
                    xsrfToken = URLDecoder.decode(value, "UTF-8")
                    break
                }
            }
        }

        // Extract current Inertia version
        val body = response.body.string()
        val match = Regex("""&quot;version&quot;:&quot;([^&]+)&quot;""").find(body)
            ?: Regex(""""version":"([^"]+)"""").find(body)

        if (match != null) {
            inertiaVersion = match.groupValues[1]
        }

        if (xsrfToken == null || inertiaVersion == null) {
            throw IOException("Failed to fetch tokens. Check if the site is accessible.")
        }
    }

    override fun getFilterList() = getFilters()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page == 1) {
            searchCursors.clear()
        }

        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val languageFilter = filters.firstInstanceOrNull<LanguageFilter>()

        val catVal = categoryFilter?.let { categories[it.vals[it.state]]?.toString() } ?: ""
        val lngVal = languageFilter?.let { languages[it.vals[it.state]] } ?: ""

        val queryBody = QueryDto(
            cat = catVal,
            lng = lngVal,
            qry = query,
            tag = "[]",
            cursor = if (page == 1) null else searchCursors[page],
        )

        val newHeaders = headers.newBuilder()
            .add("Inertia-Req", "true")
            .add("Page-Num", page.toString())
            .build()

        return POST(
            "$baseUrl/g",
            newHeaders,
            queryBody.toJsonString().toRequestBody("application/json".toMediaType()),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val page = response.request.header("Page-Num")?.toInt() ?: 1
        val data = response.parseAs<DataResponse<IndexProps>>()

        val galleries = data.props.postList.data.map { it.toSManga() }

        val nextCursor = data.props.postList.meta.nextCursor
        if (nextCursor != null) {
            searchCursors[page + 1] = nextCursor
        }

        return MangasPage(galleries, nextCursor != null)
    }

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) {
            popularCursors.clear()
        }

        val queryBody = QueryDto(
            cat = "",
            lng = "",
            qry = "",
            tag = "[]",
            cursor = if (page == 1) null else popularCursors[page],
        )

        val newHeaders = headers.newBuilder()
            .add("Inertia-Req", "true")
            .add("Page-Num", page.toString())
            .build()

        return POST(
            "$baseUrl/g",
            newHeaders,
            queryBody.toJsonString().toRequestBody("application/json".toMediaType()),
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val page = response.request.header("Page-Num")?.toInt() ?: 1
        val data = response.parseAs<DataResponse<IndexProps>>()

        val galleries = data.props.postList.data.map { it.toSManga() }

        val nextCursor = data.props.postList.meta.nextCursor
        if (nextCursor != null) {
            popularCursors[page + 1] = nextCursor
        }

        return MangasPage(galleries, nextCursor != null)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun mangaDetailsRequest(manga: SManga): Request = GET(
        "$baseUrl${manga.url}",
        headers.newBuilder().add("Inertia-Req", "true").build(),
    )

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DataResponse<DetailProps>>().props.post.data.toSManga()

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> = listOf(response.parseAs<DataResponse<DetailProps>>().props.post.data.toSChapter())

    override fun pageListRequest(chapter: SChapter): Request = GET(
        "$baseUrl${chapter.url}/read",
        headers.newBuilder().add("Inertia-Req", "true").build(),
    )

    override fun pageListParse(response: Response): List<Page> = response.parseAs<DataResponse<ReaderProps>>().props.pages.data.list.toPages()

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}

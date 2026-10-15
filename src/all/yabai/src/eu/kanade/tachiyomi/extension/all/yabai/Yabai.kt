package eu.kanade.tachiyomi.extension.all.yabai

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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder

@Source
abstract class Yabai : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::tokenInterceptor)

    private val inertiaHeaders get() = headers.newBuilder()
        .add("Inertia-Req", "true")
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
        // We do a normal GET to baseUrl (without X-Requested-With) so that client
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

    override fun getFilterList(data: JsonElement?) = getFilters()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val languageFilter = filters.firstInstanceOrNull<LanguageFilter>()

        val catVal = categoryFilter?.let { categories[it.vals[it.state]]?.toString() } ?: ""
        val lngVal = languageFilter?.let { languages[it.vals[it.state]] } ?: ""

        return fetchGalleries(page, catVal, lngVal, query, searchCursors)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchGalleries(page, "", "", "", popularCursors)

    private suspend fun fetchGalleries(
        page: Int,
        cat: String,
        lng: String,
        qry: String,
        cursors: MutableMap<Int, String>,
    ): MangasPage {
        if (page == 1) {
            cursors.clear()
        }

        val queryBody = QueryDto(
            cat = cat,
            lng = lng,
            qry = qry,
            tag = "[]",
            cursor = if (page == 1) null else cursors[page],
        )

        val data = client.post("$baseUrl/g", inertiaHeaders, queryBody.toJsonRequestBody())
            .parseAs<DataResponse<IndexProps>>()

        val galleries = data.props.postList.data.map { it.toSManga() }

        val nextCursor = data.props.postList.meta.nextCursor
        if (nextCursor != null) {
            cursors[page + 1] = nextCursor
        }

        return MangasPage(galleries, nextCursor != null)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val gallery = client.get("$baseUrl${manga.url}", inertiaHeaders)
            .parseAs<DataResponse<DetailProps>>().props.post.data
        return SMangaUpdate(gallery.toSManga(), listOf(gallery.toSChapter()))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$baseUrl${chapter.url}/read", inertiaHeaders)
        .parseAs<DataResponse<ReaderProps>>().props.pages.data.list.toPages()
}

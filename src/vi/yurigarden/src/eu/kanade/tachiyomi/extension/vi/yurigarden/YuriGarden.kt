package eu.kanade.tachiyomi.extension.vi.yurigarden

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.cryptoaes.CryptoAES
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.obj
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class YuriGarden :
    KeiSource(),
    ConfigurableSource {
    private val apiBaseUrl get() = baseUrl.replace("://", "://api.")

    private val apiUrl get() = "$apiBaseUrl/api"

    private val baseHost get() = baseUrl.toHttpUrl().host

    private val apiHost get() = apiBaseUrl.toHttpUrl().host

    private val cdnUrl get() = baseUrl.replace("://", "://cdn.")

    private val preferences by getPreferencesLazy()

    private var cachedAuthToken: String? = null

    private var authChecked = false

    private val authTokenMutex = Mutex()

    private var cachedMangaToken: String? = null

    private var cachedMangaTokenServerFn: String? = null

    private val mangaTokenMutex = Mutex()

    private val mangaTokenServerFnMutex = Mutex()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(authInterceptor())
        addInterceptor(loginRequiredInterceptor())
        addInterceptor(ImageDescrambler())
        rateLimit(15, 1.minutes) { it.host == apiHost }
    }

    private val apiHeaders: Headers
        get() = headersBuilder()
            .set("Referer", "$baseUrl/")
            .add("x-app-origin", "https://yurigarden.com")
            .add("x-custom-lang", "vi")
            .add("Accept", "application/json")
            .build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = prefShowR18
            title = "Hiển thị nội dung R18"
            summary = "Bật để hiển thị truyện có nội dung người lớn (18+)"
            setDefaultValue(prefShowR18Default)
        }.also(screen::addPreference)
    }

    private val allowR18: Boolean
        get() = preferences.getBoolean(prefShowR18, prefShowR18Default)

    // ================================ Auth =================================

    private fun authInterceptor() = Interceptor { chain ->
        val request = chain.request().newBuilder().apply {
            cachedAuthToken?.let { header("Authorization", "Bearer $it") }
        }.build()
        chain.proceed(request)
    }

    private fun loginRequiredInterceptor() = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        val responseUrl = response.request.url
        val isLoginPage = responseUrl.host == baseHost && responseUrl.encodedPath == "/login"

        if (isLoginPage) {
            cachedAuthToken = null
            authChecked = false
            response.close()
            throw IOException(loginRequiredMessage)
        }
        response
    }

    private suspend fun loadAuthToken() = authTokenMutex.withLock {
        if (authChecked) return@withLock
        cachedAuthToken = runCatching { readApiAccessToken() }.getOrNull()
        authChecked = true
    }

    private suspend fun refreshAuthToken(staleToken: String?): Boolean = authTokenMutex.withLock {
        if (cachedAuthToken != staleToken && !cachedAuthToken.isNullOrBlank()) return@withLock true

        val refreshedToken = runCatching { readApiAccessToken(staleToken) }.getOrNull()
        cachedAuthToken = refreshedToken
        authChecked = true
        !refreshedToken.isNullOrBlank() && refreshedToken != staleToken
    }

    private suspend fun authenticatedGet(url: HttpUrl): Response {
        loadAuthToken()
        val staleToken = cachedAuthToken?.takeIf(String::isNotBlank)
            ?: throw IOException(loginRequiredMessage)
        val response = client.get(url, apiHeaders)
        if (response.code != 401) return response

        response.close()
        if (!refreshAuthToken(staleToken)) throw IOException(loginRequiredMessage)

        return client.get(url, apiHeaders).also { retryResponse ->
            if (retryResponse.code == 401) {
                cachedAuthToken = null
                authChecked = false
                retryResponse.close()
                throw IOException(loginRequiredMessage)
            }
        }
    }

    private suspend fun authenticatedGet(url: String): Response = authenticatedGet(url.toHttpUrl())

    private suspend fun readApiAccessToken(staleToken: String? = null): String? {
        val pool = ('a'..'z') + ('A'..'Z')
        val bridgeName = (1..(10..20).random())
            .map { pool.random() }
            .joinToString("")
        val readAuthTokenScript = javaClass.getResource("/assets/read_auth_token.js")?.readText()
            ?: throw IllegalStateException("read_auth_token.js not found in assets")
        val script = readAuthTokenScript.replace("__AUTH_BRIDGE_NAME__", bridgeName)

        return runWebView(timeout = 10.seconds) {
            jsBridge(bridgeName) { value ->
                if (staleToken == null) {
                    resolve(value.ifBlank { null })
                } else if (value.isNotBlank() && value != staleToken) {
                    resolve(value)
                }
            }
            if (staleToken == null) {
                onPageFinished {
                    evaluateJs(script)
                }
                loadData(baseUrl, "")
            } else {
                poll(1.seconds) {
                    evaluateJs(script)
                }
                loadUrl(baseUrl)
            }
        }
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        loadAuthToken()
        val requestUrl = "$apiUrl/comics/rank/trending".toHttpUrl().newBuilder()
            .addQueryParameter("viewType", "view")
            .addQueryParameter("trendingType", "day")
            .addQueryParameter("r18", allowR18.toString())
            .build()

        val result = authenticatedGet(requestUrl).parseAs<List<TrendingComic>>()

        val mangaList = result.map { comic ->
            SManga.create().apply {
                url = "/comic/${comic.id}"
                title = comic.title
                thumbnail_url = comic.image.takeIf(String::isNotBlank)?.toThumbnailUrl()
            }
        }

        val hasNextPage = false // The trending endpoint does not support pagination

        return MangasPage(mangaList, hasNextPage)
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        loadAuthToken()
        val requestUrl = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("r18", allowR18.toString())
            .addQueryParameter("full", "true")
            .build()

        val result = authenticatedGet(requestUrl).parseAs<ComicsResponse>()

        val mangaList = result.comics.map { comic ->
            SManga.create().apply {
                url = "/comic/${comic.id}"
                title = comic.title
                thumbnail_url = comic.thumbnail?.toThumbnailUrl()
            }
        }

        val hasNextPage = result.totalPages > page

        return MangasPage(mangaList, hasNextPage)
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        loadAuthToken()
        val requestUrl = "$apiUrl/comics".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", limit.toString())
            addQueryParameter("allowR18", allowR18.toString())
            addQueryParameter("full", "true")

            setQueryParameter("searchBy", "title,anotherNames")

            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> {
                        if (filter.slug.isNotEmpty()) {
                            addQueryParameter("status", filter.slug)
                        }
                    }
                    is SortFilter -> {
                        addQueryParameter("sort", filter.slug)
                    }
                    is GenreFilter -> {
                        val selected = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.value }
                        if (selected.isNotEmpty()) {
                            addQueryParameter("genre", selected)
                        }
                    }
                    is SearchByFilter -> {
                        val selected = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.value }
                        if (selected.isNotEmpty()) {
                            setQueryParameter("searchBy", selected)
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        val result = authenticatedGet(requestUrl).parseAs<ComicsResponse>()
        val mangaList = result.comics.map { comic ->
            SManga.create().apply {
                url = "/comic/${comic.id}"
                title = comic.title
                thumbnail_url = comic.thumbnail?.toThumbnailUrl()
            }
        }
        return MangasPage(mangaList, result.totalPages > page)
    }

    // ============================== Details ===============================

    private fun mangaId(manga: SManga): String = baseUrl.toHttpUrl().resolve(manga.url)!!.pathSegments.last()

    private fun ComicDetail.toSManga() = SManga.create().apply {
        url = "/comic/${this@toSManga.id}"
        title = this@toSManga.title
        author = authors.joinToString { it.name }
        description = this@toSManga.description
        genre = genres.joinToString()
        status = when (this@toSManga.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "canceled", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = thumbnail?.toThumbnailUrl()
        initialized = true
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHost || url.pathSegments.firstOrNull() != "comic") return null
        val comicId = url.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        loadAuthToken()
        return authenticatedGet("$apiUrl/comics/$comicId")
            .parseAs<ComicDetail>()
            .toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        loadAuthToken()
        val comicId = mangaId(manga)
        val details = if (fetchDetails) {
            async {
                authenticatedGet("$apiUrl/comics/$comicId").parseAs<ComicDetail>().toSManga()
            }
        } else {
            null
        }
        val chapterList = if (fetchChapters) {
            async {
                authenticatedGet("$apiUrl/chapters/comic/$comicId")
                    .parseAs<List<ChapterData>>()
                    .toSChapters(comicId)
            }
        } else {
            null
        }

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    private fun chapterId(chapter: SChapter): String = baseUrl.toHttpUrl().resolve(chapter.url)!!.pathSegments.last()

    private fun List<ChapterData>.toSChapters(comicId: String): List<SChapter> = this
        .sortedWith(
            compareByDescending<ChapterData> { it.order }
                .thenByDescending { it.id },
        )
        .map { chapter ->
            SChapter.create().apply {
                url = "/comic/$comicId/${chapter.id}"
                name = buildString {
                    if (chapter.volume != null) {
                        append("Vol.${chapter.volume.toBigDecimal().stripTrailingZeros().toPlainString()} ")
                    }
                    if (chapter.order < 0) {
                        append("Oneshot")
                    } else {
                        append("Ch.${chapter.order.toBigDecimal().stripTrailingZeros().toPlainString()}")
                    }
                    if (chapter.name.isNotEmpty()) append(": ${chapter.name}")
                }
                date_upload = chapter.publishedAt
                chapter_number = chapter.order.toFloat()
                scanlator = chapter.team?.name ?: "Unknown"
            }
        }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        loadAuthToken()
        val response = authenticatedGet("$apiUrl/chapters/pages/${chapterId(chapter)}")
        val result = decryptIfNeeded(response)

        return result.pages.mapIndexed { index, page ->
            val rawUrl = page.url.replace("_credit", "").trimStart('/')

            if (rawUrl.startsWith("comics/") || rawUrl.startsWith("teams/")) {
                val key = page.key
                val url = "$cdnUrl/storage/v1/object/public/yuri-garden-store/$rawUrl"
                    .toHttpUrl().newBuilder().apply {
                        if (!key.isNullOrEmpty()) {
                            fragment("KEY=$key")
                        }
                    }.build().toString()
                Page(index, imageUrl = url)
            } else {
                val url = rawUrl.toHttpUrlOrNull()?.toString() ?: rawUrl
                Page(index, imageUrl = url)
            }
        }
    }

    private suspend fun decryptIfNeeded(response: Response): ChapterDetail {
        val body = response.parseAs<JsonElement>()

        return if ("encrypted" in body.obj) {
            val encrypted = body.parseAs<EncryptedResponse>()
            if (encrypted.encrypted && !encrypted.data.isNullOrEmpty()) {
                decryptChapterDetail(encrypted.data)
            } else {
                body.parseAs()
            }
        } else {
            body.parseAs()
        }
    }

    private suspend fun decryptChapterDetail(data: String): ChapterDetail {
        val token = getMangaToken(forceRefresh = false)
        return runCatching {
            CryptoAES.decrypt(data, token).parseAs<ChapterDetail>()
        }.getOrElse {
            cachedMangaToken = null
            val refreshedToken = getMangaToken(forceRefresh = true)
            CryptoAES.decrypt(data, refreshedToken).parseAs<ChapterDetail>()
        }
    }

    private suspend fun getMangaToken(forceRefresh: Boolean): String = mangaTokenMutex.withLock {
        if (!forceRefresh) cachedMangaToken?.let { return@withLock it }

        val headers = headersBuilder()
            .set("Referer", "$baseUrl/")
            .set("Accept", "application/json")
            .set("x-tsr-serverFn", "true")
            .build()

        val token = client
            .get("$baseUrl/_serverFn/${getMangaTokenServerFn()}", headers)
            .parseAs<ServerFnNode>()
            .let { extractServerFnValue(it, "token") }
            ?: throw IOException("Không lấy được khóa giải mã chương")

        cachedMangaToken = token
        token
    }

    private suspend fun getMangaTokenServerFn(): String = mangaTokenServerFnMutex.withLock {
        cachedMangaTokenServerFn?.let { return@withLock it }

        val html = client.get(baseUrl, headers).use { it.body.string() }

        val mainScript = mainScriptRegex.find(html)?.groupValues?.get(1)
            ?: throw IOException("Không tìm thấy bundle chính")
        val mainScriptUrl = mainScript.toHttpUrlOrNull()?.toString() ?: "$baseUrl$mainScript"
        val mainScriptBody = client.get(mainScriptUrl, headers).use { it.body.string() }

        val routeIndex = mainScriptBody.indexOf(chapterRoutePath)
        val searchBody = if (routeIndex > 0) {
            mainScriptBody.substring(0, routeIndex).takeLast(20_000)
        } else {
            mainScriptBody
        }
        val serverFn = serverFnRegex.findAll(searchBody)
            .lastOrNull()
            ?.groupValues
            ?.get(1)
            ?: throw IOException("Không tìm thấy khóa server function")

        cachedMangaTokenServerFn = serverFn
        serverFn
    }

    private fun extractServerFnValue(node: ServerFnNode, key: String): String? {
        val props = node.p ?: return null
        val index = props.k.indexOf(key)
        if (index >= 0) {
            props.v.getOrNull(index)?.s?.stringOrNull?.let { return it }
        }

        return props.v.firstNotNullOfOrNull { extractServerFnValue(it, key) }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client
        .get("$apiBaseUrl/resources/systems_vi.json", apiHeaders)
        .parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data
            ?.parseAs<SystemResources>()
            ?.genres
            ?.values
            ?.map { it.name to it.slug }
            .orEmpty()

        return getFilters(genres)
    }

    // =============================== Related ================================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        loadAuthToken()
        val result = authenticatedGet("$apiUrl/comics/related/${mangaId(manga)}")
            .parseAs<List<Comic>>()

        return result.map { comic ->
            SManga.create().apply {
                url = "/comic/${comic.id}"
                title = comic.title
                thumbnail_url = comic.thumbnail?.toThumbnailUrl()
            }
        }
    }

    // ============================= Utilities ==============================

    private fun String.toThumbnailUrl(): String = if (startsWith("http")) this else "$cdnUrl/storage/v1/object/public/yuri-garden-store/${trimStart('/')}"

    private val limit = 15
    private val chapterRoutePath = "/comic/\$comicId/\$chapterId/"
    private val loginRequiredMessage = "Nguồn này cần đăng nhập bằng webview để xem"
    private val prefShowR18 = "pref_show_r18"
    private val prefShowR18Default = false

    private val mainScriptRegex = Regex("""(?:src|href)="([^"]*/assets/main-[^"]+\.js)"""")
    private val serverFnRegex = Regex(
        """(?:const|let|var)\s+[A-Za-z_$][\w$]*\s*=\s*[A-Za-z_$][\w$]*\(\{method:"GET"\}\)\.handler\([A-Za-z_$][\w$]*\("([A-Za-z0-9]+)"\)\)""",
    )
}

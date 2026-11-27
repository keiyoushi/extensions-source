package eu.kanade.tachiyomi.extension.all.lunaranime

import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.TURNSTILE_AREA
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Source
abstract class LunarAnime :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_AUTO_UNLOCK
            title = "Auto-unlock with ad points"
            summary = "Earn free ad points and unlock points-locked chapters automatically"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    override fun OkHttpClient.Builder.configureClient() = this
        .addInterceptor(::authorize)
        .addInterceptor(VeilInterceptor(CDN_HOST))
        .rateLimit(2) { it.host == API_HOST }
        .rateLimit(4) { it.host == CDN_HOST }

    private fun authorize(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = client.cookieJar.loadForRequest(request.url).firstOrNull { it.name == AUTHORIZATION }?.value
        if (request.url.host != API_HOST || token == null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, token).build())
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", SortFilter.POPULAR)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = API_URL.toHttpUrl().newBuilder().apply {
            addPathSegments("api/manga/recent")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "30")

            if (lang != "all") {
                addQueryParameter("language", lang)
            }
        }.build()

        val result = client.get(url, Fetch.XHR).parseAs<LunarRecentResponse>()
        return MangasPage(
            mangas = result.mangas.filter { it.slug.isNotEmpty() }.map { it.toSManga() },
            hasNextPage = (result.page * result.limit) < result.totalCount,
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = API_URL.toHttpUrl().newBuilder().apply {
            addPathSegments("api/manga/search")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "30")
            if (query.isNotBlank()) {
                addQueryParameter("query", query)
            }

            if (lang != "all") {
                addQueryParameter("language", lang)
            }

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", filter.toUriPart())
                        addQueryParameter("order", if (filter.state?.ascending == true) "asc" else "desc")
                    }
                    is StatusFilter -> filter.toUriPart()?.let { addQueryParameter("status", it) }
                    is TypeFilter -> filter.toUriPart()?.let { addQueryParameter("country", it) }
                    is FormatFilter -> filter.toUriPart()?.let { addQueryParameter("format", it) }
                    is ContentFilter -> filter.toUriPart()?.let { addQueryParameter("content", it) }
                    is LanguageFilter -> filter.toUriPart()?.let { addQueryParameter("language", it) }
                    is YearFilter -> filter.state.toIntOrNull()?.let { addQueryParameter("year", it.toString()) }
                    is GenreFilter -> {
                        filter.included.takeIf { it.isNotEmpty() }?.let { addQueryParameter("genres", it.joinToString(",")) }
                        filter.excluded.takeIf { it.isNotEmpty() }?.let { addQueryParameter("exclude_genres", it.joinToString(",")) }
                    }
                    else -> {}
                }
            }
        }.build()

        val result = client.get(url, Fetch.XHR).parseAs<LunarSearchResponse>()
        return MangasPage(
            // The site lists blank placeholder rows
            mangas = result.manga.filter { it.slug.isNotEmpty() }.map { it.toSManga() },
            hasNextPage = result.page < result.totalPages,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] != "manga") return null

        val manga = SManga.create().apply { this.url = "/manga/${segments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast("/")

        return coroutineScope {
            val details = async { if (fetchDetails) fetchMangaDetails(slug) else manga }
            val chapterList = async { if (fetchChapters) fetchChapterList(slug) else chapters }
            SMangaUpdate(manga = details.await(), chapters = chapterList.await())
        }
    }

    private suspend fun fetchMangaDetails(slug: String): SManga {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/title")
            .addPathSegment(slug)
            .build()

        return client.get(url, Fetch.XHR).parseAs<LunarMangaResponse>().manga.toSManga()
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/manga")
            .addPathSegment(manga.url.substringAfterLast("/"))
            .addPathSegment("similar")
            .addQueryParameter("limit", "18")
            .build()
        return client.get(url, Fetch.XHR).parseAs<LunarSimilarResponse>().data.map { it.toSManga() }
    }

    override fun getChapterUrl(chapter: SChapter): String = (baseUrl + chapter.url).toHttpUrl().newBuilder()
        .query(null)
        .build()
        .toString()

    private suspend fun fetchChapterList(slug: String): List<SChapter> {
        val passwordUrl = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/password/info")
            .addPathSegment(slug)
            .build()
        val requestUrl = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/manga")
            .addPathSegment(slug)
            .build()
        val (passwordInfo, result) = coroutineScope {
            val passwordInfo = async { client.get(passwordUrl, Fetch.XHR).parseAs<LunarPasswordInfoResponse>() }
            val result = async { client.get(requestUrl, Fetch.XHR).parseAs<LunarChapterListResponse>() }
            passwordInfo.await() to result.await()
        }

        return result.data.filter {
            lang == "all" || it.language == lang
        }.map { chapter ->
            val isLocked = passwordInfo.hasSeriesPassword ||
                passwordInfo.chapterPasswords.any {
                    it.chapterNumber == chapter.chapter && (it.language == null || it.language == chapter.language)
                }
            chapter.toSChapter(slug, isLocked, showLanguage = lang == "all")
        }.reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = (baseUrl + chapter.url).toHttpUrl()
        val (slug, chapterNumber) = chapterUrl.pathSegments.takeLast(2)
        val language = chapterUrl.queryParameter("lang") ?: "en"
        val script = "(${javaClass.getResource(READER_SCRIPT)!!.readText()})(${slug.toJsonString()}, ${chapterNumber.toJsonString()}, ${language.toJsonString()})"

        val state = readerMutex.withLock { readerTab(chapterUrl.toString()).read(chapterUrl.toString(), script) }

        val pages = when {
            state.price != null -> {
                if (!autoUnlock) {
                    val balance = state.balance?.let { ", balance $it" }.orEmpty()
                    error(
                        "This chapter is unlocked with ad points (price ${state.price}$balance). " +
                            "Turn on \"Auto-unlock with ad points\" in the extension settings, or unlock it in WebView.",
                    )
                }
                unlock(slug, chapterNumber, language, state.price, state.balance ?: 0)
                val unlocked = readerMutex.withLock { readerTab(chapterUrl.toString()).read(chapterUrl.toString(), script) }
                unlocked.pages ?: error("Unlocked, but the chapter did not load. Open it in WebView.")
            }
            state.reason != null -> error(if ("account" in state.reason) LOGIN_MESSAGE else state.message ?: state.reason)
            else -> state.pages.orEmpty()
        }

        return pages.mapIndexed { index, page ->
            val url = page.url.toHttpUrl()
            Page(index, imageUrl = url.newBuilder().fragment(page.veil?.let { url.resolve(it)?.toString() }).build().toString())
        }
    }

    private suspend fun unlock(slug: String, chapter: String, language: String, price: Int, balance: Int) {
        var points = balance
        var attempts = 0
        while (points < price) {
            if (attempts++ >= MAX_EARN_ATTEMPTS) error("Not enough ad points yet ($points of $price). Try again shortly.")
            val earned = earnPoints()
            if (earned == null || earned <= points) error("Could not earn ad points right now ($points of $price). Try again shortly.")
            points = earned
        }

        val buyUrl = API_URL.toHttpUrl().newBuilder().addPathSegments("api/points-lock/buy").build()
        val body = LunarBuyBody("manga", slug, chapter, language).toJsonRequestBody()
        val result = client.post(buyUrl, Fetch.XHR, body, ensureSuccess = false).parseAs<LunarBuyResponse>()
        if (!result.success) error(result.error ?: "Could not unlock this chapter")
    }

    private suspend fun earnPoints(): Int? {
        val grantUrl = baseUrl.toHttpUrl().newBuilder().addPathSegments("px/q").addQueryParameter("p", "1").build()
        val grant = client.post(grantUrl, Fetch.XHR, "".toRequestBody(), ensureSuccess = false).use { response ->
            if (!response.isSuccessful) return null
            decodeGrant(response.body.string())
        } ?: return null

        val heartbeatUrl = API_URL.toHttpUrl().newBuilder().addPathSegments("api/presence/heartbeat").build()
        val banked = client.post(heartbeatUrl, Fetch.XHR, LunarHeartbeatBody(grant).toJsonRequestBody(), ensureSuccess = false)
            .use { it.isSuccessful }
        if (!banked) return null

        val pointsUrl = API_URL.toHttpUrl().newBuilder().addPathSegments("api/ads/points").build()
        return client.get(pointsUrl, Fetch.XHR).parseAs<LunarAdPoints>().points
    }

    private fun decodeGrant(raw: String): String? {
        val bytes = Base64.decode(raw.trim(), Base64.DEFAULT)
        if (bytes.size < 4) return null
        val decoded = CharArray(bytes.size) { i ->
            val key = if (i < 4) 0 else bytes[(i - 4) % 4].toInt() and 0xFF
            ((bytes[i].toInt() and 0xFF) xor key).toChar()
        }
        return GRANT_REGEX.find(String(decoded))?.value
    }

    private val readerMutex = Mutex()

    private val tabScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var tab: ReaderTab? = null

    private fun readerTab(url: String): ReaderTab = tab?.takeUnless { it.closed } ?: ReaderTab(url).also { tab = it }

    // One reader tab, kept between chapters
    private inner class ReaderTab(firstUrl: String) {
        private val ready = CompletableDeferred<WebViewScope<Unit>>()

        private var opened = false

        @Volatile
        private var navigations = 0

        @Volatile
        private var lastUsed = TimeSource.Monotonic.markNow()

        @Volatile
        private var failure: Throwable? = null

        @Volatile
        var closed = false
            private set

        init {
            tabScope.launch {
                try {
                    runWebView<Unit>(TAB_TIMEOUT) {
                        onPageStarted { navigations++ }
                        poll(IDLE_TIMEOUT / 4) {
                            if (lastUsed.elapsedNow() > IDLE_TIMEOUT) resolve(Unit)
                        }
                        loadUrl(firstUrl)
                        ready.complete(this)
                    }
                } catch (e: Throwable) {
                    failure = e
                    ready.completeExceptionally(e)
                } finally {
                    closed = true
                }
            }
        }

        suspend fun read(url: String, script: String): LunarReaderState {
            val scope = ready.await()
            val before = if (opened) navigations else 0
            if (opened) scope.evaluateJs("location.assign(${url.toJsonString()})")
            opened = true

            var deadline = TimeSource.Monotonic.markNow() + READ_TIMEOUT
            var checkSince: TimeMark? = null
            var shown = false
            try {
                while (true) {
                    if (closed) throw failure as? IOException ?: IOException("The reader was closed, try again", failure)
                    lastUsed = TimeSource.Monotonic.markNow()
                    val state = if (navigations > before) scope.evaluate(script)?.parseAs<LunarReaderState?>() else null
                    if (state != null && (state.pages != null || state.price != null || state.reason != null)) return state
                    if (state?.check == true) {
                        val since = checkSince ?: TimeSource.Monotonic.markNow().also { checkSince = it }
                        if (since.elapsedNow() >= CHECK_GRACE) {
                            if (!shown) deadline = TimeSource.Monotonic.markNow() + CHECK_TIMEOUT
                            shown = withContext(Dispatchers.Main) { scope.showToUser(if (state.turnstile) TURNSTILE_AREA else state.area) }
                            if (!shown) throw IOException("Open the chapter in WebView to pass the site's check")
                        }
                    } else if (state != null && shown) {
                        scope.hideFromUser()
                        shown = false
                        checkSince = null
                        deadline = TimeSource.Monotonic.markNow() + READ_TIMEOUT
                    }
                    if (deadline.hasPassedNow()) throw IOException("The chapter did not load, open it in WebView")
                    delay(POLL_INTERVAL)
                }
            } finally {
                if (shown) scope.hideFromUser()
                scope.evaluateJs("location.replace('about:blank')")
            }
        }

        private suspend fun WebViewScope<Unit>.evaluate(script: String): String? = withTimeoutOrNull(SCRIPT_TIMEOUT) {
            suspendCancellableCoroutine { continuation -> evaluateJs(script) { continuation.resume(it) } }
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = API_URL.toHttpUrl().newBuilder().addPathSegments("api/manga/search/facets").build()
        return client.get(url, Fetch.XHR).parseAs<JsonElement>()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SortFilter())
            add(StatusFilter())
            add(TypeFilter())
            add(FormatFilter())
            add(ContentFilter())
            if (lang == "all") add(LanguageFilter())
            add(YearFilter())
            data?.parseAs<LunarFacetsDto>()?.tags?.takeIf { it.isNotEmpty() }?.let { tags -> add(GenreFilter(tags.map { it.name })) }
        },
    )

    companion object {
        private const val API_HOST = "api.lunarx.to"
        private const val API_URL = "https://$API_HOST"
        private const val LOGIN_MESSAGE = "Log in via WebView to read chapters"
        private const val CDN_HOST = "vault.lunarx.to"
        private const val READER_SCRIPT = "/assets/lunar-reader.js"
        private val TAB_TIMEOUT = 24.hours
        private val IDLE_TIMEOUT = 30.minutes
        private val READ_TIMEOUT = 60.seconds
        private val CHECK_TIMEOUT = 3.minutes
        private val CHECK_GRACE = 5.seconds
        private val POLL_INTERVAL = 500.milliseconds
        private val SCRIPT_TIMEOUT = 5.seconds

        private const val AUTHORIZATION = "Authorization"
        private const val PREF_AUTO_UNLOCK = "auto_unlock_points"
        private const val MAX_EARN_ATTEMPTS = 3
        private val GRANT_REGEX = Regex("""[0-9]{13}\.[A-Za-z0-9_-]{16}\.[A-Za-z0-9_-]{43}""")
    }

    private val autoUnlock get() = preferences.getBoolean(PREF_AUTO_UNLOCK, false)
}

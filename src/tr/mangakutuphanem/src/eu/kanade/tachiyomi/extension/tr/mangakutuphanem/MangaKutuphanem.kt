package eu.kanade.tachiyomi.extension.tr.mangakutuphanem

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class MangaKutuphanem : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host

    private val apiUrl get() = "$baseUrl/api/v1"

    private val preferences by getPreferencesLazy()

    private val tokenMutex = Mutex()

    private var lastManifest: Pair<String, ChapterPagesDto>? = null

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::expiredPageInterceptor)
        .addInterceptor(::authInterceptor)
        .rateLimit(8) { it.host == host }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/popular".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val mangas = client.get(url, Fetch.XHR).parseAs<ResultsDto>().results.map { it.toSManga(apiUrl) }
        // The site's own client stops paging popular here; the API has no end marker.
        return MangasPage(mangas, page < POPULAR_MAX_PAGE)
    }

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
                .addQueryParameter("all", "1")
                .build()
            val mangas = client.get(url, Fetch.XHR).parseAs<ResultsDto>().results
                .filter { it.kind != "novel" }
                .map { it.toSManga(apiUrl) }
            return MangasPage(mangas, false)
        }

        val url = "$apiUrl/public/recent".toHttpUrl().newBuilder().apply {
            addQueryParameter("kind", "manga")
            addQueryParameter("offset", ((page - 1) * RECENT_PAGE_SIZE).toString())
            filters.firstInstanceOrNull<SortFilter>()?.sort?.let { addQueryParameter("sort", it) }
            filters.firstInstanceOrNull<SourceFilter>()?.selected?.let { addQueryParameter("source", it) }
        }.build()
        val result = client.get(url, Fetch.XHR).parseAs<RecentDto>()
        return MangasPage(result.items.map { it.toSManga(apiUrl) }, !result.done)
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/sources", Fetch.XHR)
        .parseAs<List<SourceDto>>()
        .filter { it.kind == "manga" }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val sources = data?.parseAs<List<SourceDto>>() ?: return FilterList(SortFilter())
        return FilterList(SortFilter(), SourceFilter(sources.map { it.name to it.id }))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != host) return null
        val (route, source, slug) = url.pathSegments.takeIf { it.size >= 3 } ?: return null
        if (route != "seri-bilgi") return null
        val manga = SManga.create().apply { this.url = "/seri-bilgi/$source/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = fetchSeries(manga).series
        return SMangaUpdate(
            manga.apply { series.applyTo(this, apiUrl) },
            series.toSChapters(),
        )
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga) = fetchSeries(manga).alternates.orEmpty().map { it.toSManga(apiUrl) }

    private suspend fun fetchSeries(manga: SManga): SeriesResponseDto {
        val (_, _, source, slug) = manga.url.split("/")
        val url = "$apiUrl/public/series".toHttpUrl().newBuilder()
            .addQueryParameter("source", source)
            .addQueryParameter("slug", slug)
            .build()
        return client.get(url, Fetch.XHR).parseAs<SeriesResponseDto>()
    }

    override fun getChapterUrl(chapter: SChapter) = chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val manifest = fetchManifest(chapter.url)
        return manifest.pages.mapIndexed { index, page ->
            Page(index, imageUrl = imageUrl(manifest.source, page, chapter.url))
        }
    }

    private suspend fun fetchManifest(chapterUrl: String) = client.get(chapterManifestUrl(chapterUrl), Fetch.XHR, ensureSuccess = false)
        .toManifest()

    // Also runs inside the page interceptor, so it only throws IOException.
    private fun Response.toManifest(): ChapterPagesDto {
        if (isSuccessful) return parseAs<ChapterPagesDto>()
        val error = if (body.contentType()?.subtype == "json") parseAs<ErrorDto>().error else null
        close()
        if (error == "LINK_ONLY") {
            throw IOException("This chapter is only readable on the source site. Open it in WebView")
        }
        throw IOException("HTTP error $code")
    }

    private fun chapterManifestUrl(chapterUrl: String) = "$apiUrl/chapter".toHttpUrl().newBuilder()
        .addQueryParameter("url", chapterUrl)
        .addQueryParameter("device", deviceId)
        .build()

    private fun imageUrl(source: String, page: PageDto, chapterUrl: String) = "$apiUrl/image".toHttpUrl().newBuilder()
        .addQueryParameter("source", source)
        .addQueryParameter("ref", page.ref)
        .addQueryParameter("exp", page.exp.toString())
        .addQueryParameter("sig", page.sig)
        .fragment(chapterUrl)
        .build()
        .toString()

    private val deviceId: String
        get() = preferences.getString(DEVICE_ID_PREF, null)
            ?: UUID.randomUUID().toString().also { preferences.edit().putString(DEVICE_ID_PREF, it).apply() }

    // Page signatures expire after a few hours and belong to the session that fetched them, and the app caches
    // page lists: an expired or refused page gets a fresh manifest, kept for the chapter's other pages so a long
    // chapter costs one manifest request, not one per page.
    private fun expiredPageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val chapterUrl = request.url.fragment ?: return chain.proceed(request)
        val exp = request.url.queryParameter("exp")?.toLongOrNull() ?: return chain.proceed(request)
        if (!isExpired(exp)) {
            val response = chain.proceed(request)
            if (response.code != 403) return response
            response.close()
        }

        val sig = request.url.queryParameter("sig")
        val manifest = synchronized(this) {
            lastManifest
                ?.takeIf { (url, manifest) -> url == chapterUrl && manifest.pages.none { isExpired(it.exp) || it.sig == sig } }
                ?.second
                ?: chain.proceed(request.newBuilder().url(chapterManifestUrl(chapterUrl)).fetch(Fetch.XHR).build())
                    .toManifest()
                    .also { lastManifest = chapterUrl to it }
        }
        val ref = request.url.queryParameter("ref")
        val page = manifest.pages.firstOrNull { it.ref == ref } ?: throw IOException("Page not found")
        return chain.proceed(request.newBuilder().url(imageUrl(manifest.source, page, chapterUrl)).build())
    }

    private fun isExpired(exp: Long) = Instant.fromEpochSeconds(exp) - Clock.System.now() < 1.minutes

    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (request.url.host != host || !path.startsWith("/api/v1/") || path.startsWith("/api/v1/public/")) {
            return chain.proceed(request)
        }
        val token = runBlocking { getToken() }
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }

    // The site's anonymous Firebase session, kept across launches like the site's client does.
    private suspend fun getToken(): String = tokenMutex.withLock {
        if (Clock.System.now().toEpochMilliseconds() < preferences.getLong(EXPIRES_PREF, 0L)) {
            return preferences.getString(TOKEN_PREF, null)!!
        }
        val token = preferences.getString(REFRESH_PREF, null)?.let { refresh(it) } ?: signUp()
        val expires = Clock.System.now() + token.expiresIn.toLong().seconds - 1.minutes
        preferences.edit()
            .putString(TOKEN_PREF, token.idToken)
            .putString(REFRESH_PREF, token.refreshToken)
            .putLong(EXPIRES_PREF, expires.toEpochMilliseconds())
            .apply()
        token.idToken
    }

    private suspend fun refresh(refreshToken: String): TokenDto? {
        val url = SECURE_TOKEN_URL.toHttpUrl().newBuilder()
            .addQueryParameter("key", API_KEY)
            .build()
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()
        return try {
            client.post(url, Fetch.XHR, body).parseAs<TokenDto>()
        } catch (e: HttpException) {
            if (e.code != 400) throw e
            null
        }
    }

    private suspend fun signUp(): TokenDto {
        val url = SIGN_UP_URL.toHttpUrl().newBuilder()
            .addQueryParameter("key", API_KEY)
            .build()
        return client.post(url, Fetch.XHR, SignUpBody().toJsonRequestBody()).parseAs<TokenDto>()
    }

    companion object {
        private const val API_KEY = "AIzaSyA1Vriam47KWEtZMWl3Keg4QcwKQN847aE"
        private const val SIGN_UP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signUp"
        private const val SECURE_TOKEN_URL = "https://securetoken.googleapis.com/v1/token"
        private const val TOKEN_PREF = "token"
        private const val REFRESH_PREF = "refresh_token"
        private const val EXPIRES_PREF = "token_expires"
        private const val DEVICE_ID_PREF = "device_id"
        private const val RECENT_PAGE_SIZE = 60
        private const val POPULAR_MAX_PAGE = 60
    }
}

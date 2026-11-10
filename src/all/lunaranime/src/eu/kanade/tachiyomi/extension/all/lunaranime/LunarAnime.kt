package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class LunarAnime : KeiSource() {
    private val serenity by lazy { LunarSerenity(API_URL, id) }

    private class Clearance(val thumbprint: String, val expiresAt: Instant)

    private val clearances = ConcurrentHashMap<String, Clearance>()

    private fun hasClearance(slug: String): Boolean {
        val clearance = clearances[slug] ?: return false
        return clearance.thumbprint == serenity.thumbprint && clearance.expiresAt > Clock.System.now()
    }

    private fun markCleared(slug: String, ttlSeconds: Long?) {
        val ttl = ((ttlSeconds ?: DEFAULT_CLEARANCE_TTL_S) - CLEARANCE_MARGIN_S).coerceAtLeast(0).seconds
        clearances[slug] = Clearance(serenity.thumbprint, Clock.System.now() + ttl)
    }

    private var cachedSiteKey: String? = null

    private suspend fun siteKey(): String {
        cachedSiteKey?.let { return it }
        val url = API_URL.toHttpUrl().newBuilder().addPathSegments("api/captcha/config").build()
        return client.get(url, Fetch.XHR).parseAs<LunarCaptchaConfig>().mangaTurnstileSitekey
            .also { cachedSiteKey = it }
    }

    // Turnstile token bound to the chapter token's nonce, as in the site's reader
    private suspend fun solveTurnstile(nonce: String): String = getTurnstileToken(
        url = baseUrl,
        siteKey = siteKey(),
        action = "read",
        cData = nonce,
    )

    override fun OkHttpClient.Builder.configureClient() = this
        .addInterceptor(serenity.interceptor())
        .rateLimit(2) { it.host == API_HOST || it.host == CDN_HOST }

    private val crypto by lazy { LunarDecryptor(serenity) }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", SortFilter.POPULAR)

    // =============================== Latest ===============================

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

    // =============================== Search ===============================

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

    // =========================== Manga Details ============================

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

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        val url = chapter.url.substringBefore("?")
        return baseUrl + url
    }

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

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = (baseUrl + chapter.url).toHttpUrl()
        val language = chapterUrl.queryParameter("lang") ?: "en"
        val (slug, chapterNumber) = chapterUrl.pathSegments.takeLast(2)

        val seeds = crypto.extractSeeds(client.get(chapterUrl, Fetch.DOCUMENT).asJsoup())

        // Without this call first the page list is decoys
        val chapterLockUrl = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/chapter-lock")
            .addPathSegment(slug)
            .build()
        client.get(chapterLockUrl, Fetch.XHR, ensureSuccess = false).close()

        var result = fetchChapter(seeds, slug, chapterNumber, language, withTurnstile = !hasClearance(slug))

        if (result.needsRevalidation()) {
            clearances.remove(slug)
            serenity.reset()
            result = fetchChapter(seeds, slug, chapterNumber, language, withTurnstile = true)
        }

        if (result.needsRevalidation()) {
            error("Could not load images. Open in WebView and try again.")
        }

        markCleared(slug, result.cacheTtl)

        val viewUrl = API_URL.toHttpUrl().newBuilder().addPathSegments("api/manga/chapter/view").build()
        val viewBody = ViewRequestBody(slug, chapterNumber, language).toJsonRequestBody()
        client.post(viewUrl, Fetch.XHR, viewBody, ensureSuccess = false).close()

        return result.data.images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private suspend fun fetchChapter(
        seeds: LunarSeeds,
        slug: String,
        chapterNumber: String,
        language: String,
        withTurnstile: Boolean,
    ): LunarPageListDecrypted {
        val minted = crypto.mint(seeds, slug, chapterNumber)
        val turnstileToken = if (withTurnstile) solveTurnstile(minted.nonce) else null

        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/r")
            .addPathSegment(minted.token)
            .apply { if (language != "en") addQueryParameter("language", language) }
            .build()

        val requestHeaders = headersBuilder().apply {
            if (turnstileToken != null) {
                set("Catch-Me-If-You-Can-Monkey", turnstileToken)
                set("Which-Monkey", "turnstile")
            }
        }.build()

        val sessionData = client.get(url, requestHeaders, Fetch.XHR).parseAs<LunarPageListResponse>().data.sessionData

        return crypto.unpack(sessionData, seeds, minted.nonce).parseAs<LunarPageListDecrypted>()
    }

    private fun LunarPageListDecrypted.needsRevalidation(): Boolean {
        val images = data.images
        return cacheStatus == "revalidate" ||
            images.isEmpty() ||
            images.all { it.contains(DECOY_PATH) }
    }

    // ============================== Filters ===============================

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
        private const val CDN_HOST = "vault.lunarx.to"
        private const val DECOY_PATH = "/api/cdn/p/"
        private const val DEFAULT_CLEARANCE_TTL_S = 300L
        private const val CLEARANCE_MARGIN_S = 30L
    }
}

package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class LunarAnime : KeiSource() {

    private val internalLang: String = when (lang) {
        "pt-BR" -> "pt-br"
        else -> lang
    }

    private val apiurlHost by lazy { API_URL.toHttpUrl().host }
    private val cdnurlHost by lazy { CDN_URL.toHttpUrl().host }

    private val serenity by lazy { LunarSerenity(API_URL, id) }

    private class Clearance(val thumbprint: String, val expiresAt: Long)

    private val clearances = ConcurrentHashMap<String, Clearance>()

    private fun hasClearance(slug: String): Boolean {
        val clearance = clearances[slug] ?: return false
        return clearance.thumbprint == serenity.thumbprint && clearance.expiresAt > System.currentTimeMillis()
    }

    private fun markCleared(slug: String, ttlSeconds: Long?) {
        val ttlMs = ((ttlSeconds ?: DEFAULT_CLEARANCE_TTL_S) - CLEARANCE_MARGIN_S).coerceAtLeast(0) * 1000
        clearances[slug] = Clearance(serenity.thumbprint, System.currentTimeMillis() + ttlMs)
    }

    private var cachedSiteKey: String? = null

    private suspend fun siteKey(): String {
        cachedSiteKey?.let { return it }
        val key = runCatching {
            client.get("$API_URL/api/captcha/config".toHttpUrl())
                .parseAs<LunarCaptchaConfig>()
                .readerPrimarySitekey
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: FALLBACK_SITE_KEY
        cachedSiteKey = key
        return key
    }

    private suspend fun solveTurnstile(): String = getTurnstileToken(url = baseUrl, siteKey = siteKey())

    override fun OkHttpClient.Builder.configureClient() = this
        .addInterceptor(serenity.interceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            if (url.contains(cdnurlHost)) {
                val newRequest = request.newBuilder()
                    .header("Referer", "$baseUrl/")
                    .build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(request)
            }
        }
        .rateLimit(2) { it.host == apiurlHost || it.host == cdnurlHost }

    private val crypto by lazy { LunarDecryptor(serenity) }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = API_URL.toHttpUrl().newBuilder().apply {
            addPathSegments("api/manga/recent")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "30")

            if (lang != "all") {
                addQueryParameter("language", internalLang)
            }
        }.build()

        val result = client.get(url).parseAs<LunarRecentResponse>()
        return MangasPage(
            mangas = result.mangas.map { it.toSManga() },
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
                addQueryParameter("language", internalLang)
            }

            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> filter.toValue()?.let { addQueryParameter("status", it) }
                    is TypeFilter -> filter.toValue()?.let { addQueryParameter("country", it) }
                    is LanguageFilter -> filter.toValue()?.let { addQueryParameter("language", it) }
                    is YearFilter -> {
                        val year = filter.state
                        if (year.isNotBlank() && year.toIntOrNull() != null) {
                            addQueryParameter("year", year)
                        }
                    }
                    is GenreFilter -> {
                        val genres = filter.toGenres()
                        if (genres.isNotEmpty()) {
                            addQueryParameter("genres", genres.joinToString(","))
                        }
                    }
                    else -> {}
                }
            }
            addQueryParameter("sort", "relevance")
        }.build()

        val result = client.get(url).parseAs<LunarSearchResponse>()
        return MangasPage(
            mangas = result.manga.map { it.toSManga() },
            hasNextPage = result.page < result.totalPages,
        )
    }

    // =========================== Manga Details ============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] != "manga") return null

        return fetchMangaDetails(segments[1])
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast("/")

        return SMangaUpdate(
            manga = if (fetchDetails) fetchMangaDetails(slug) else manga,
            chapters = if (fetchChapters) fetchChapterList(slug) else chapters,
        )
    }

    private suspend fun fetchMangaDetails(slug: String): SManga {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/title")
            .addPathSegment(slug)
            .build()

        return client.get(url).parseAs<LunarMangaResponse>().manga.toSManga()
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
        val passwordInfo = client.get(passwordUrl).parseAs<LunarPasswordInfoResponse>()

        val requestUrl = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/manga")
            .addPathSegment(slug)
            .build()
        val result = client.get(requestUrl).parseAs<LunarChapterListResponse>()

        return result.data.filter {
            lang == "all" || it.language == internalLang
        }.map { chapter ->
            val isLocked = passwordInfo.hasSeriesPassword ||
                passwordInfo.chapterPasswords.any {
                    it.chapterNumber == chapter.chapter && (it.language == null || it.language == chapter.language)
                }
            chapter.toSChapter(slug, isLocked)
        }.reversed()
    }

    // =============================== Pages ================================

    private suspend fun viewChapter(slug: String, number: String, lang: String) {
        val ratingUrl = "$API_URL/api/manga/rating/status/$slug/$number"
        val ratingResp = client.get(ratingUrl, ensureSuccess = false)
        ratingResp.close()

        val body = ViewRequestBody(slug, number, lang).toJsonRequestBody()
        val viewUrl = "$API_URL/api/manga/chapter/view"
        val viewResp = client.post(viewUrl, body, ensureSuccess = false)
        viewResp.close()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = (baseUrl + chapter.url).toHttpUrl()
        val language = chapterUrl.queryParameter("lang") ?: "en"
        val (slug, chapterNumber) = chapterUrl.pathSegments.takeLast(2)

        val seeds = crypto.extractSeeds(client.get(chapterUrl).asJsoup())

        // Required requests or fake images are returned
        viewChapter(slug, chapterNumber, language)

        val token = if (hasClearance(slug)) null else solveTurnstile()
        var result = fetchChapter(seeds, slug, chapterNumber, language, token)

        if (result.needsRevalidation()) {
            clearances.remove(slug)
            serenity.reset()
            result = fetchChapter(seeds, slug, chapterNumber, language, solveTurnstile())
        }

        if (result.needsRevalidation()) {
            error("Could not load images. Open in WebView and try again.")
        }

        markCleared(slug, result.cacheTtl)

        return result.data.images.mapIndexed { index, imageUrl ->
            Page(index, chapter.url, imageUrl)
        }
    }

    private suspend fun fetchChapter(
        seeds: LunarSeeds,
        slug: String,
        chapterNumber: String,
        language: String,
        turnstileToken: String?,
    ): LunarPageListDecrypted {
        val minted = crypto.mint(seeds, slug, chapterNumber)

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

        val sessionData = client.get(url, requestHeaders).parseAs<LunarPageListResponse>()
            .data?.sessionData
            ?: error("session_data is empty")

        return crypto.unpack(sessionData, seeds, minted.nonce).parseAs<LunarPageListDecrypted>()
    }

    private fun LunarPageListDecrypted.needsRevalidation(): Boolean {
        val images = data.images
        return cacheStatus == "revalidate" ||
            images.isEmpty() ||
            images.all { it.contains(DECOY_PATH) }
    }

    override fun getImageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Referer", baseUrl + page.url)
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            StatusFilter(),
            TypeFilter(),
        )

        if (lang == "all") {
            filters.add(LanguageFilter())
        }

        filters.addAll(
            listOf(
                YearFilter(),
                GenreFilter(),
            ),
        )

        return FilterList(filters)
    }

    companion object {
        private const val API_URL = "https://api.lunarx.to"
        private const val CDN_URL = "https://vault.lunarx.to"
        private const val DECOY_PATH = "/api/cdn/p/"
        private const val FALLBACK_SITE_KEY = "0x4AAAAAAEweOgJ6zfOKhkgq"
        private const val DEFAULT_CLEARANCE_TTL_S = 300L
        private const val CLEARANCE_MARGIN_S = 30L
    }
}

package eu.kanade.tachiyomi.extension.id.comicaso

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class Comicaso :
    KeiSource(),
    ConfigurableSource {

    private val defaultUserAgent: String? by lazy {
        headersBuilder()
        cachedDefaultUserAgent
    }
    private var cachedDefaultUserAgent: String? = null

    // Android Chrome UA is the default fallback used by Mihon's WebView (for
    // both solving this site's Cloudflare challenge and the Google sign-in
    // step). It is NOT sent on background API calls — see authInterceptor.
    // If either Cloudflare or Google starts rejecting this default for a
    // given user, they can override it via Settings > Random user agent
    // instead of requiring an extension update.
    override fun Headers.Builder.configureHeaders(): Headers.Builder {
        if (cachedDefaultUserAgent == null) {
            cachedDefaultUserAgent = build()["User-Agent"]
        }
        return set("User-Agent", DEFAULT_USER_AGENT)
            .set("X-Comicaso-Platform", "web")
            .setRandomUserAgent(
                filterInclude = listOf("Chrome", "Safari"),
            )
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::authInterceptor)
        .addInterceptor(::cdnInterceptor)
        .rateLimit(4)

    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .apply { defaultUserAgent?.let { header("User-Agent", it) } }
            .build()

        val response = chain.proceed(request)
        if (response.code == 403) {
            val peekBody = response.peekBody(1024).string()
            if (peekBody.contains("\"locked\":true")) {
                response.close()
                throw IOException("Login wajib untuk membuka konten ini. Buka di WebView dan login dengan Google.")
            }
        }
        return response
    }

    private fun cdnInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.isSuccessful || response.code == 403 || response.code == 401) {
            return response
        }

        val host = request.url.host
        val prefix = HOST_TO_PREFIX[host] ?: return response

        response.close()

        for (domain in CDN_DOMAINS) {
            val newUrl = request.url.newBuilder()
                .host("$prefix.$domain")
                .build()
            val newRequest = request.newBuilder().url(newUrl).build()
            val newResponse = chain.proceed(newRequest)
            if (newResponse.isSuccessful) {
                return newResponse
            }
            newResponse.close()
        }

        return chain.proceed(request)
    }

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val response = client.get("$baseUrl/api/trending.php?period=all&limit=$PAGE_SIZE")
        val res = response.parseAs<TrendingResponseDto>()
        return MangasPage(res.data.map { it.toSManga() }, false)
    }

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * PAGE_SIZE
        val url = "$baseUrl/api/home.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("source", "all")
            addQueryParameter("q", "")
            addQueryParameter("mode", "update")
            addQueryParameter("type", "all")
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("offset", offset.toString())
        }.build()

        val response = client.get(url)
        val res = response.parseAs<HomeResponseDto>()
        return MangasPage(res.data.map { it.toSManga() }, res.hasMore)
    }

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val offset = (page - 1) * PAGE_SIZE
        val source = filters.firstInstanceOrNull<SourceFilter>()?.toUriPart() ?: "all"
        val type = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart() ?: "all"
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart() ?: ""

        val url = "$baseUrl/api/home.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("source", source)
            addQueryParameter("q", query)
            addQueryParameter("mode", "update")
            addQueryParameter("type", type)
            addQueryParameter("genre", genre)
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("offset", offset.toString())
        }.build()

        val response = client.get(url)
        val res = response.parseAs<HomeResponseDto>()
        return MangasPage(res.data.map { it.toSManga() }, res.hasMore)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = if (url.queryParameter("page") == "manga") {
            url.queryParameter("slug")
        } else if (url.pathSegments.size >= 2) {
            url.pathSegments[1].takeIf { it.isNotEmpty() }
        } else {
            null
        } ?: return null

        val source = url.queryParameter("source") ?: url.pathSegments.firstOrNull() ?: "all"
        val manga = SManga.create().apply {
            this.url = "$source/$slug"
        }
        return runCatching {
            getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }.getOrNull()
    }

    // ============================== Details & Chapters ===================
    override fun getMangaUrl(manga: SManga): String {
        val segments = manga.urlSegments()
        val source = segments.getOrNull(0) ?: "all"
        val slug = segments.getOrNull(1) ?: ""
        return "$baseUrl/?page=manga&source=$source&slug=$slug"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val segments = chapter.urlSegments()
        val source = segments.getOrNull(0) ?: "all"
        val manga = segments.getOrNull(1) ?: ""
        val slug = segments.getOrNull(2) ?: ""
        return "$baseUrl/?page=chapter&source=$source&manga=$manga&chapter=$slug"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val segments = manga.urlSegments()
        val source = segments.getOrNull(0) ?: "all"
        val slug = segments.getOrNull(1) ?: ""
        val response = client.get("$baseUrl/api/manga.php?source=$source&slug=$slug&platform=web")
        val res = response.parseAs<MangaDetailResponseDto>()
        val parsedChapters = res.data.chapters?.map { it.toSChapter(source, res.data.slug) }
            ?.sortedWith(
                compareByDescending<SChapter> { chapter ->
                    chapterNumberRegex.find(chapter.name)?.groupValues?.get(1)?.toFloatOrNull()
                        ?: chapterNumberFallbackRegex.find(chapter.name)?.value?.toFloatOrNull()
                        ?: chapterNumberFallbackRegex.find(chapter.url.substringAfterLast('/'))?.value?.toFloatOrNull()
                        ?: -1f
                }.thenByDescending { it.name },
            )
            ?: emptyList()
        return SMangaUpdate(
            manga = res.data.toSManga(source),
            chapters = parsedChapters,
        )
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val source = url.pathSegments.getOrNull(0) ?: "all"
        val manga = url.pathSegments.getOrNull(1) ?: ""
        val slug = url.pathSegments.getOrNull(2) ?: ""
        val token = url.queryParameter("token")!!

        val apiUri = "$baseUrl/api/chapter.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("source", source)
            addQueryParameter("manga", manga)
            addQueryParameter("chapter", slug)
            addQueryParameter("token", token)
        }.build()

        val response = client.get(apiUri)
        val res = response.parseAs<ChapterResponseDto>()
        return res.data.images.orEmpty().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ==============================
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SourceFilter(),
        TypeFilter(),
        GenreFilter(),
    )

    // ============================ Preferences =============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()
    }

    // ============================= Utilities =============================
    private fun SManga.urlSegments() = "$baseUrl/$url".toHttpUrl().pathSegments

    private fun SChapter.urlSegments() = "$baseUrl/$url".toHttpUrl().pathSegments

    companion object {
        private const val PAGE_SIZE = 60
        private val chapterNumberRegex = Regex("""(?i)(?:bab|chapter|ch|ep|episode)\s*(?:[-:]\s*)?(\d+(?:\.\d+)?)""")
        private val chapterNumberFallbackRegex = Regex("""\d+(?:\.\d+)?""")
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36"

        private val CDN_DOMAINS = listOf("basrat.online", "gurihnyoh.site", "jeletot.fun")
        private val HOST_TO_PREFIX = mapOf(
            "ap.imgmanga.com" to "tilu",
            "ap2.imgmanga.com" to "opat",
            "cdn.imgmacha.com" to "hiji",
            "cdn2.imgmacha.com" to "dua",
        )
    }
}

package eu.kanade.tachiyomi.extension.id.comicaso

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException

class Comicaso :
    HttpSource(),
    ConfigurableSource {

    override val name = "Comicaso"

    override val baseUrl = "https://v3.comicaso.pro"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::authInterceptor)
        .rateLimit(4)
        .build()

    private val defaultUserAgent by lazy {
        super.headersBuilder().build()["User-Agent"]
    }

    // Android Chrome UA is the default fallback used by Mihon's WebView (for
    // both solving this site's Cloudflare challenge and the Google sign-in
    // step). It is NOT sent on background API calls — see authInterceptor.
    // If either Cloudflare or Google starts rejecting this default for a
    // given user, they can override it via Settings > Random user agent
    // instead of requiring an extension update.
    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("User-Agent", DEFAULT_USER_AGENT)
        .setRandomUserAgent(
            filterInclude = listOf("Chrome", "Safari"),
        )

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

    // ============================== Popular ==============================

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        if (page > 1) return Observable.just(MangasPage(emptyList(), false))
        return super.fetchPopularManga(page)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/trending.php?period=all&limit=$PAGE_SIZE", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.parseAs<TrendingResponseDto>()
        return MangasPage(res.data.map { it.toSManga() }, false)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val offset = (page - 1) * PAGE_SIZE
        val url = "$baseUrl/api/home.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("source", "all")
            addQueryParameter("q", "")
            addQueryParameter("mode", "update")
            addQueryParameter("type", "all")
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("offset", offset.toString())
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val res = response.parseAs<HomeResponseDto>()
        return MangasPage(res.data.map { it.toSManga() }, res.hasMore)
    }

    // ============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrlOrNull()
            if (url != null) {
                val source = url.queryParameter("source")
                val slug = url.queryParameter("slug")

                if (url.queryParameter("page") == "manga" && source != null && slug != null) {
                    val manga = SManga.create().apply { this.url = "$source/$slug" }
                    return fetchMangaDetails(manga).map { MangasPage(listOf(it), false) }
                }
            }
            return Observable.just(MangasPage(emptyList(), false))
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String {
        val segments = manga.urlSegments()
        val source = segments.getOrNull(0) ?: "all"
        val slug = segments.getOrNull(1) ?: ""
        return "$baseUrl/?page=manga&source=$source&slug=$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val segments = manga.urlSegments()
        val source = segments.getOrNull(0) ?: "all"
        val slug = segments.getOrNull(1) ?: ""
        return GET("$baseUrl/api/manga.php?source=$source&slug=$slug&platform=web", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val res = response.parseAs<MangaDetailResponseDto>()
        val source = response.request.url.queryParameter("source") ?: "all"
        return res.data.toSManga(source)
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val res = response.parseAs<MangaDetailResponseDto>()
        val source = response.request.url.queryParameter("source") ?: "all"
        return res.data.chapters?.map { it.toSChapter(source, res.data.slug) }
            ?.sortedWith(
                compareByDescending<SChapter> { chapter ->
                    chapterNumberRegex.find(chapter.name)?.groupValues?.get(1)?.toFloatOrNull()
                        ?: chapterNumberFallbackRegex.find(chapter.name)?.value?.toFloatOrNull()
                        ?: chapterNumberFallbackRegex.find(chapter.url.substringAfterLast('/'))?.value?.toFloatOrNull()
                        ?: -1f
                }.thenByDescending { it.name },
            )
            ?: emptyList()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val segments = chapter.urlSegments()
        val source = segments.getOrNull(0) ?: "all"
        val manga = segments.getOrNull(1) ?: ""
        val slug = segments.getOrNull(2) ?: ""
        return "$baseUrl/?page=chapter&source=$source&manga=$manga&chapter=$slug"
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = chapter.urlSegments()
        val source = segments.getOrNull(0) ?: "all"
        val manga = segments.getOrNull(1) ?: ""
        val slug = segments.getOrNull(2) ?: ""
        return GET("$baseUrl/api/chapter.php?source=$source&manga=$manga&chapter=$slug", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val res = response.parseAs<ChapterResponseDto>()
        return res.data.images.orEmpty().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
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
    }
}

package eu.kanade.tachiyomi.extension.zh.komiic

import android.util.Base64
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import rx.Observable
import java.io.IOException

class Komiic :
    HttpSource(),
    ConfigurableSource {
    override val name = "Komiic"
    override val lang = "zh"
    override val baseUrl by lazy {
        mirrorUrls[pref.getString(BASE_URL_PREF, "0")!!.toInt().coerceAtMost(mirrorUrls.size - 1)]
    }
    override val supportsLatest = true
    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            refreshToken(chain)
            val origin = chain.request()
            chain.proceed(origin).also {
                if (it.code == 402 && origin.url.toString().contains("api/image")) {
                    it.close()
                    throw IOException("今日圖片讀取次數已達上限，請登录或明天再來！")
                }
            }
        }.build()

    private fun refreshToken(chain: Interceptor.Chain) {
        val url = chain.request().url
        if (url.pathSegments[0] != "api") return
        val cookie = client.cookieJar.loadForRequest(url).find { it.name == "komiic-access-token" } ?: return
        val parts = cookie.value.split(".")
        if (parts.size != 3) throw IOException("Token 格式無效")
        val payload = Base64.decode(parts[1], Base64.DEFAULT).decodeToString()
        if (System.currentTimeMillis() + 3600_000 < payload.parseAs<JwtPayload>().exp * 1000) return
        val response = chain.proceed(POST("$baseUrl/auth/refresh", headers)).apply { close() }
        if (!response.isSuccessful) throw IOException("刷新 Token 失敗：HTTP ${response.code}")
    }

    private val pref by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context).forEach(screen::addPreference)
    }

    // Customize ===================================================================================

    private val SManga.id get() = url.substringAfterLast("/")
    private val SChapter.id get() = url.substringAfterLast("/")

    private fun RequestBody.request(fragment: String? = null): Request {
        val extra = fragment?.let { "#$it" } ?: ""
        return POST("$baseUrl/api/query$extra", headers, this)
    }

    private fun Response.parse() = parseAs<ResponseDto>().getData()

    // Popular Manga ===============================================================================

    override fun popularMangaRequest(page: Int): Request {
        val pagination = Pagination((page - 1) * PAGE_SIZE, OrderBy.MONTH_VIEWS)
        return commonQuery(ListingVariables(pagination)).request()
    }

    override fun popularMangaParse(response: Response) = parseListing(response.parse())

    // Latest Updates ==============================================================================

    override fun latestUpdatesRequest(page: Int): Request {
        val pagination = Pagination((page - 1) * PAGE_SIZE, OrderBy.DATE_UPDATED)
        return commonQuery(ListingVariables(pagination)).request()
    }

    override fun latestUpdatesParse(response: Response) = parseListing(response.parse())

    // Search Manga ================================================================================

    override fun getFilterList() = buildFilterList()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host != baseUrl.toHttpUrl().host) {
                throw Exception("不支持这个 URL")
            }
            val id = url.pathSegments[1]
            return fetchSearchManga(page, PREFIX_ID_SEARCH + id, filters)
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = if (query.startsWith(PREFIX_ID_SEARCH)) {
        idsQuery(query.removePrefix(PREFIX_ID_SEARCH)).request()
    } else if (query.isNotBlank()) {
        searchQuery(query).request()
    } else {
        val variables = ListingVariables(Pagination((page - 1) * PAGE_SIZE))
        filters.filterIsInstance<KomiicFilter>().forEach { it.apply(variables) }
        listingQuery(variables).request()
    }

    override fun searchMangaParse(response: Response) = parseListing(response.parse())

    // Manga Details ===============================================================================

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga) = mangaDetailQuery(manga.id).request()

    override fun mangaDetailsParse(response: Response) = response.parse().comicById!!.toSManga()

    // Chapter List ================================================================================

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url + "/images/all"

    override fun chapterListRequest(manga: SManga) = chapterListQuery(manga.id).request(manga.id)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parse()
        val chapters = data.chaptersByComicId!!.toMutableList()
        when (pref.getString(CHAPTER_FILTER_PREF, "all")) {
            "chapter" -> chapters.retainAll { it.type == "chapter" }
            "book" -> chapters.retainAll { it.type == "book" }
            else -> {}
        }
        chapters.sortWith(
            compareByDescending<ChapterDto> { it.type }.thenByDescending { it.serial.toFloatOrNull() },
        )
        val mangaUrl = "/comic/${response.request.url.fragment}"
        return chapters.map { it.toSChapter(mangaUrl) }
    }

    // Page List ===================================================================================

    override fun pageListRequest(chapter: SChapter) = pageListQuery(chapter.id).request(chapter.url)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parse()
        val chapterUrl = response.request.url.fragment!!
        return data.imagesByChapterId!!.mapIndexed { index, image ->
            Page(index, "$chapterUrl/page/${index + 1}", "$baseUrl/api/image/${image.kid}")
        }
    }

    // Image =======================================================================================

    override fun imageRequest(page: Page) = super.imageRequest(page).newBuilder()
        .addHeader("accept", "*/*").addHeader("referer", page.url).build()

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}

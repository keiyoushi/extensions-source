package eu.kanade.tachiyomi.extension.zh.komiic

import android.util.Base64
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Komiic :
    HttpSource(),
    ConfigurableSource {
    override val name = "Komiic"
    override val baseUrl = "https://komiic.com"
    override val lang = "zh"
    override val supportsLatest = true
    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            refreshToken(chain)
            chain.proceed(chain.request())
        }
        .build()

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

    private val apiUrl = "$baseUrl/api/query"
    private val preferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context).forEach(screen::addPreference)
    }

    // Customize ===================================================================================

    private val SManga.id get() = url.substringAfterLast("/")
    private val SChapter.id get() = url.substringAfterLast("/")

    private fun RequestBody.request() = POST(apiUrl, headers, this)
    private fun Response.parse() = parseAs<ResponseDto>().getData()

    // Popular Manga ===============================================================================

    override fun popularMangaRequest(page: Int): Request {
        val pagination = Pagination((page - 1) * PAGE_SIZE, OrderBy.MONTH_VIEWS)
        return listingQuery(ListingVariables(pagination)).request()
    }

    override fun popularMangaParse(response: Response) = parseListing(response.parse())

    // Latest Updates ==============================================================================

    override fun latestUpdatesRequest(page: Int): Request {
        val pagination = Pagination((page - 1) * PAGE_SIZE, OrderBy.DATE_UPDATED)
        return listingQuery(ListingVariables(pagination)).request()
    }

    override fun latestUpdatesParse(response: Response) = parseListing(response.parse())

    // Search Manga ================================================================================

    override fun getFilterList() = buildFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.startsWith(PREFIX_ID_SEARCH)) {
        idsQuery(query.removePrefix(PREFIX_ID_SEARCH)).request()
    } else if (query.isNotBlank()) {
        searchQuery(query).request()
    } else {
        val variables = ListingVariables(Pagination((page - 1) * PAGE_SIZE))
        for (filter in filters) if (filter is KomiicFilter) filter.apply(variables)
        listingQuery(variables).request()
    }

    override fun searchMangaParse(response: Response) = parseListing(response.parse())

    // Manga Details ===============================================================================

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga) = mangaQuery(manga.id).request()

    override fun mangaDetailsParse(response: Response) = response.parse().comicById!!.toSManga()

    // Chapter List ================================================================================

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url + "/images/all"

    override fun chapterListRequest(manga: SManga) = mangaQuery(manga.id).request()

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parse()
        val chapters = data.chaptersByComicId!!.toMutableList()
        when (preferences.getString(CHAPTER_FILTER_PREF, "all")!!) {
            "chapter" -> chapters.retainAll { it.type == "chapter" }
            "book" -> chapters.retainAll { it.type == "book" }
            else -> {}
        }
        chapters.sortWith(
            compareByDescending<ChapterDto> { it.type }
                .thenByDescending { it.serial.toFloatOrNull() },
        )
        val mangaUrl = data.comicById!!.url
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return chapters.map { it.toSChapter(mangaUrl, dateFormat) }
    }

    // Page List ===================================================================================

    override fun pageListRequest(chapter: SChapter): Request = pageListQuery(chapter.id).request().newBuilder()
        .tag(String::class.java, chapter.url)
        .build()

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parse()
        val check = preferences.getBoolean(CHECK_API_LIMIT_PREF, true)
        if (check && data.reachedImageLimit!!) {
            throw Exception("今日圖片讀取次數已達上限，請登录或明天再來！")
        }
        val chapterUrl = response.request.tag(String::class.java)!!
        return data.imagesByChapterId!!.mapIndexed { index, image ->
            Page(index, "$chapterUrl/page/${index + 1}", "$baseUrl/api/image/${image.kid}")
        }
    }

    // Image =======================================================================================

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .addHeader("accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .addHeader("referer", page.url)
        .build()

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}

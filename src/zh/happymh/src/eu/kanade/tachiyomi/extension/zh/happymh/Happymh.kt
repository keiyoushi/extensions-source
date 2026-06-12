package eu.kanade.tachiyomi.extension.zh.happymh

import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.zh.happymh.ChapterByPageResponse
import eu.kanade.tachiyomi.extension.zh.happymh.ChapterByPageResponseData
import eu.kanade.tachiyomi.extension.zh.happymh.Decoder
import eu.kanade.tachiyomi.extension.zh.happymh.PageListResponseDto
import eu.kanade.tachiyomi.extension.zh.happymh.PopularResponseDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import rx.Observable
import uy.kohesive.injekt.injectLazy

const val PREF_KEY_CUSTOM_UA = "pref_key_custom_ua_"

class Happymh :
    HttpSource(),
    ConfigurableSource {
    override val name: String = "嗨皮漫画"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true

    override val baseUrl: String = "https://m.happymh.com"
    private val json: Json by injectLazy()

    private val preferences = getPreferences()

    private val decoder = Decoder()

    init {
        val oldUa = preferences.getString("userAgent", null)
        if (oldUa != null) {
            val editor = preferences.edit().remove("userAgent")
            if (oldUa.isNotBlank()) editor.putString(PREF_KEY_CUSTOM_UA, oldUa)
            editor.apply()
        }
    }

    private val rewriteOctetStream: Interceptor = Interceptor { chain ->
        val originalResponse: Response = chain.proceed(chain.request())
        if (originalResponse.headers("Content-Type")
                .contains("application/octet-stream") && originalResponse.request.url.toString()
                .contains(".jpg")
        ) {
            val orgBody = originalResponse.body.source()
            val newBody = orgBody.asResponseBody("image/jpeg".toMediaType())
            originalResponse.newBuilder()
                .body(newBody)
                .build()
        } else {
            originalResponse
        }
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(rewriteOctetStream)
        .build()

    override fun headersBuilder(): Headers.Builder {
        val builder = super.headersBuilder()
        val userAgent = preferences.getString(PREF_KEY_CUSTOM_UA, "")!!
        return if (userAgent.isNotBlank()) {
            builder.set("User-Agent", userAgent)
        } else {
            builder
        }
    }

    // Popular

    // Requires login, otherwise result is the same as latest updates
    override fun popularMangaRequest(page: Int): Request {
        val headers = headersBuilder().add("Referer", "$baseUrl/latest").build()
        return GET("$baseUrl/apis/c/index?pn=$page&series_status=-1&order=views", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<PopularResponseDto>().data

        val items = data.items.map {
            SManga.create().apply {
                title = it.name
                url = it.url
                thumbnail_url = it.cover
            }
        }
        val hasNextPage = data.isEnd.not()

        return MangasPage(items, hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val headers = headersBuilder().add("Referer", "$baseUrl/latest").build()
        return GET("$baseUrl/apis/c/index?pn=$page&series_status=-1&order=last_date", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val body = FormBody.Builder()
                .addEncoded("searchkey", query)
                .add("v", "v2.13")
                .build()

            val headers = headersBuilder()
                .add("Referer", "$baseUrl/sssearch")
                .build()

            return POST("$baseUrl/v2.0/apis/manga/ssearch", headers, body)
        }
        val url = "$baseUrl/apis/c/index".toHttpUrl().newBuilder()
        filters.filterIsInstance<UriPartFilter>().forEach {
            if (it.selected.isNotEmpty()) {
                url.addQueryParameter(it.key, it.selected)
            }
        }
        val headers = headersBuilder().add("Referer", "$baseUrl/latest/${url.build().query}").build()
        url.addQueryParameter("pn", page.toString())
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.encodedPath.contains("/apis/c/index")) {
            // for filter response
            return popularMangaParse(response)
        }
        return MangasPage(popularMangaParse(response).mangas, false)
    }

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        AreaFilter(),
        AudienceFilter(),
        StatusFilter(),
    )

    // Details

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("div.mg-property > h2.mg-title")!!.text()
        thumbnail_url = document.selectFirst("div.mg-cover > mip-img")!!.attr("abs:src")
        author = document.selectFirst("div.mg-property > p.mg-sub-title:nth-of-type(2)")!!.text()
        artist = author
        genre = document.select("div.mg-property > p.mg-cate > a").eachText().joinToString(", ")
        description =
            document.selectFirst("div.manga-introduction > mip-showmore#showmore")!!.text()
    }

    // Chapters

    private fun fetchChapterByPage(comicId: String, page: Int): ChapterByPageResponseData {
        val requestId = System.currentTimeMillis().toString()
        val url = "$baseUrl/v2.0/apis/manga/chapterByPage".toHttpUrl().newBuilder()
            .addQueryParameter("code", comicId)
            .addQueryParameter("lang", "cn")
            .addQueryParameter("order", "asc")
            .addQueryParameter("page", "$page")
            .addQueryParameter("_t", requestId)
            .build()
        return client.newCall(GET(url, ajaxHeadersBuilder(requestId).build())).execute()
            .parseAs<ChapterByPageResponse>().data
    }

    private fun fetchChapterByPage(manga: SManga, page: Int): ChapterByPageResponseData {
        val comicId = "$baseUrl${manga.url}".toHttpUrl().pathSegments.last()
        return fetchChapterByPage(comicId, page)
    }

    private fun fetchChapterByPageAsObservable(
        manga: SManga,
        page: Int,
    ): Observable<Pair<Int, ChapterByPageResponseData>> = Observable.just(fetchChapterByPage(manga, page)).concatMap {
        if (it.isPageEnd()) {
            Observable.just(page to it)
        } else {
            Observable.just(page to it)
                .concatWith(fetchChapterByPageAsObservable(manga, page + 1))
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val comicId = "$baseUrl${manga.url}".toHttpUrl().pathSegments.last()
        return Observable
            .defer {
                fetchChapterByPageAsObservable(manga, 1)
            }
            .map {
                it.second.items.map { data ->
                    SChapter.create().apply {
                        name = data.chapterName
                        // create a dummy chapter url : /comic_id/dummy_mark/chapter_id#expect_page
                        url = "/$comicId/$DUMMY_CHAPTER_MARK/${data.id}#${it.first}"
                    }
                }
            }
            .toList()
            .map { it.flatten().reversed() }
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl${chapter.url}".toHttpUrl()
        val comicId = url.pathSegments[0]
        val chapterId = url.pathSegments[2]
        return "$baseUrl/mangaread/$comicId/$chapterId"
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        if (!chapter.url.contains(DUMMY_CHAPTER_MARK)) {
            // Old format is detected
            throw Exception("请刷新章节列表")
        }
        val requestId = System.currentTimeMillis().toString()
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()
        val comicId = chapterUrl.pathSegments[0]
        val chapterId = chapterUrl.pathSegments[2]

        val url = "$baseUrl/v2.0/apis/manga/reading".toHttpUrl().newBuilder()
            .addQueryParameter("code", comicId)
            .addQueryParameter("cid", chapterId)
            .addQueryParameter("v", "v4.300101")
            .addQueryParameter("_t", requestId)
            .build()
        val headers = ajaxHeadersBuilder(requestId, accept = "application/json")
            .set("Referer", "$baseUrl/mangaread/$comicId/$chapterId")
            .build()

        // Replicate the _ga_HVJMXGJXFJ cookie generation from the website (VQ/VB in main.*.js).
        // gaTimestamp = 10-digit seconds timestamp + 3-digit checksum from a lookup table.
        val gaTimestamp = generateGaTimestamp()
        val cookie = Cookie.Builder()
            .name("_ga_HVJMXGJXFJ")
            .value("GS2.1.s${gaTimestamp}\$o9\$g1\$t${gaTimestamp + 99999}\$j43\$l0\$h0")
            .domain(baseUrl.toHttpUrl().host)
            .path("/")
            .expiresAt(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
            .build()
        client.cookieJar.saveFromResponse(url, listOf(cookie))

        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<PageListResponseDto>()

        val pages = if (dto.data.isEncode) {
            decoder.decodeScans(dto.data.scans)
        } else {
            dto.data.scans
        }

        return pages.parseAs<List<PageDto>>()
            // If n == 1, the image is from next chapter
            .filter { it.n == 0 }
            .mapIndexed { index, page ->
                Page(index, imageUrl = page.url.substringBefore("?q="))
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, headers)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context

        EditTextPreference(context).apply {
            key = PREF_KEY_CUSTOM_UA
            title = "User Agent"
            summary = "留空则使用应用设置中的默认 User Agent，重启生效"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    Headers.headersOf("User-Agent", newValue as String)
                    true
                } catch (e: Throwable) {
                    Toast.makeText(context, "User Agent 无效：${e.message}", Toast.LENGTH_LONG)
                        .show()
                    false
                }
            }
        }.let(screen::addPreference)
    }

    private fun ajaxHeadersBuilder(
        requestId: String,
        accept: String = "application/json, text/plain, */*",
    ): Headers.Builder = headersBuilder()
        .set("Accept", accept)
        .add("X-Requested-With", "XMLHttpRequest")
        .add("X-Requested-Id", requestId)

    private fun ChapterByPageResponseData.isPageEnd(): Boolean = isEnd == 1 || items.isEmpty()

    /**
     * Corresponds to the VB() function in the website's main.*.js.
     * Generates a 13-digit pseudo-millisecond timestamp:
     * 1. Take the Unix timestamp in seconds (10 digits).
     * 2. Use the last 3 digits as indices into a hardcoded lookup table.
     * 3. Sum the 3 looked-up values, take the first 3 chars as a checksum.
     * 4. Concatenate: seconds(10) + checksum(3) = 13-digit timestamp.
     */
    private fun generateGaTimestamp(): Long {
        // Digit-to-value lookup table from the obfuscated JS source
        val table = intArrayOf(335, 984, 248, 485, 524, 559, 486, 165, 114, 103)
        val seconds = (System.currentTimeMillis() / 1000).toString()
        val len = seconds.length
        val sum = table[seconds[len - 3] - '0'] +
            table[seconds[len - 2] - '0'] +
            table[seconds[len - 1] - '0']
        return (seconds + sum.toString().take(3)).toLong()
    }

    companion object {
        private const val DUMMY_CHAPTER_MARK = "dummy-mark"
    }
}

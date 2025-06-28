package eu.kanade.tachiyomi.extension.zh.komiic

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Komiic : HttpSource(), ConfigurableSource {
    override var name = "Komiic"
    override val baseUrl = "https://komiic.com"
    override val lang = "zh"
    override val supportsLatest = true
    override val client = network.cloudflareClient

    private val queryAPIUrl = "$baseUrl/api/query"
    private val preferences = getPreferences()

    companion object {
        const val PAGE_SIZE = 20
        const val PREFIX_ID_SEARCH = "id:"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context).forEach(screen::addPreference)
    }

    // Hot Comic
    override fun popularMangaRequest(page: Int): Request {
        val variables = Variables().set(
            "pagination",
            Pagination((page - 1) * PAGE_SIZE, "MONTH_VIEWS"),
        ).build()
        val payload = Payload("hotComics", variables, QUERY_HOT_COMICS)
        return POST(queryAPIUrl, headers, payload.toJsonRequestBody())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.parseAs<Data<List<Comic>>>()
        val comics = res.data.result
        return MangasPage(comics.map(Comic::toSManga), comics.size == PAGE_SIZE)
    }

    // Recent update
    override fun latestUpdatesRequest(page: Int): Request {
        val variables = Variables().set(
            "pagination",
            Pagination((page - 1) * PAGE_SIZE, "DATE_UPDATED"),
        ).build()
        val payload = Payload("recentUpdate", variables, QUERY_RECENT_UPDATE)
        return POST(queryAPIUrl, headers, payload.toJsonRequestBody())
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    /**
     * 根據 ID 搜索漫畫
     * Search the comic based on the ID.
     */
    private fun comicByIDRequest(id: String): Request {
        val variables = Variables().set("comicId", id).build()
        val payload = Payload("comicById", variables, QUERY_COMIC_BY_ID)
        return POST(queryAPIUrl, headers, payload.toJsonRequestBody())
    }

    /**
     * 根據 ID 解析搜索來的漫畫
     * Parse the comic based on the ID.
     */
    private fun parseComicByID(response: Response): MangasPage {
        val res = response.parseAs<Data<Comic>>()
        val entries = listOf(res.data.result.toSManga())
        return MangasPage(entries, false)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val variables = Variables().set("keyword", query).build()
        val payload = Payload("searchComicAndAuthorQuery", variables, QUERY_SEARCH)
        return POST(queryAPIUrl, headers, payload.toJsonRequestBody())
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            client.newCall(comicByIDRequest(query.substringAfter(PREFIX_ID_SEARCH)))
                .asObservableSuccess()
                .map(::parseComicByID)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val res = response.parseAs<Data<Result<List<Comic>>>>()
        val comics = res.data.result.result
        return MangasPage(comics.map(Comic::toSManga), comics.size == PAGE_SIZE)
    }

    // Comic details
    override fun mangaDetailsRequest(manga: SManga) = comicByIDRequest(manga.url.substringAfterLast("/"))

    override fun mangaDetailsParse(response: Response): SManga {
        val res = response.parseAs<Data<Comic>>()
        return res.data.result.toSManga()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    /**
     * 解析日期
     * Parse date
     */
    private fun parseDate(dateStr: String): Long {
        return try {
            DATE_FORMAT.parse(dateStr)?.time ?: 0L
        } catch (e: ParseException) {
            e.printStackTrace()
            0L
        }
    }

    // Chapter list
    override fun chapterListRequest(manga: SManga): Request {
        val variables = Variables().set("comicId", manga.url.substringAfterLast("/")).build()
        val payload = Payload("chapterByComicId", variables, QUERY_CHAPTER)
        return POST("$queryAPIUrl#${manga.url}", headers, payload.toJsonRequestBody())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val res = response.parseAs<Data<List<Chapter>>>()
        val comics = res.data.result.sortedWith(
            compareByDescending<Chapter> { it.type }
                .thenByDescending { it.serial.toFloatOrNull() },
        )
        val display = preferences.getString(CHAPTER_FILTER_PREF, "all")
        val items = when (display) {
            "chapter" -> comics.filter { it.type == "chapter" }
            "book" -> comics.filter { it.type == "book" }
            else -> comics
        }
        val comicUrl = response.request.url.fragment
        return items.map {
            SChapter.create().apply {
                url = "$comicUrl/chapter/${it.id}/page/1"
                name = when (it.type) {
                    "chapter" -> "第 ${it.serial} 話"
                    "book" -> "第 ${it.serial} 卷"
                    else -> it.serial
                }
                scanlator = "${it.size}P"
                date_upload = parseDate(it.dateUpdated)
                chapter_number = if (it.type == "book") 0F else it.serial.toFloatOrNull() ?: -1f
            }
        }
    }

    /**
     * 檢查 API 是否達到上限
     * Check if the API has reached its limit.
     *
     * (Idk how to throw an exception in reading page)
     */
    private fun checkAPILimit(): Observable<Boolean> {
        val payload = Payload("reachedImageLimit", Variables().build(), QUERY_API_LIMIT)
        val response = client.newCall(POST(queryAPIUrl, headers, payload.toJsonRequestBody()))
        val limit = response.asObservableSuccess().map { it.parseAs<Data<Boolean>>().data.result }
        return limit
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val fetchData = client.newCall(pageListRequest(chapter))
            .asObservableSuccess().map(::pageListParse)
        if (preferences.getBoolean(CHECK_API_LIMIT_PREF, false)) {
            return Observable.zip(checkAPILimit(), fetchData) { isLimitReached, pages ->
                require(!isLimitReached) { "今日圖片讀取次數已達上限，請登录或明天再來！" }
                pages
            }
        }
        return fetchData
    }

    // Page list
    override fun pageListRequest(chapter: SChapter): Request {
        val variables = Variables().set(
            "chapterId",
            chapter.url.substringAfter("/chapter/").substringBefore("/page/"),
        ).build()
        val payload = Payload("imagesByChapterId", variables, QUERY_PAGE_LIST)
        return POST("$queryAPIUrl#${chapter.url}", headers, payload.toJsonRequestBody())
    }

    override fun pageListParse(response: Response): List<Page> {
        val res = response.parseAs<Data<List<Image>>>()
        val chapterUrl = response.request.url.toString().split("#")[1]
        return res.data.result.mapIndexed { index, image ->
            Page(
                index,
                "${chapterUrl.substringBeforeLast("/")}/$index",
                "$baseUrl/api/image/${image.kid}",
            )
        }
    }

    override fun imageRequest(page: Page): Request {
        return super.imageRequest(page).newBuilder()
            .addHeader("accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8'")
            .addHeader("referer", page.url)
            .build()
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private inline fun <reified T> Payload<T>.toJsonRequestBody() = this.toJsonString().toRequestBody(JSON_MEDIA_TYPE)
}

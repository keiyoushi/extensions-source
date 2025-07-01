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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Komiic : HttpSource(), ConfigurableSource {
    override var name = "Komiic"
    override val baseUrl = "https://komiic.com"
    override val lang = "zh"
    override val supportsLatest = true
    override val client = network.cloudflareClient

    private val apiUrl = "$baseUrl/api/query"
    private val preferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context).forEach(screen::addPreference)
    }

    // Customize ===================================================================================

    companion object {
        const val PAGE_SIZE = 20
        const val PREFIX_ID_SEARCH = "id:"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val SManga.id get() = url.substringAfterLast("/")
    private val SChapter.id
        get() = url.substringAfter("/chapter/").substringBefore("/page/")

    private inline fun <reified T> Payload<T>.toRequestBody() =
        this.toJsonString().toRequestBody(JSON_MEDIA_TYPE)

    /**
     * 根據 ID 搜索漫畫
     * Search the comic based on the ID.
     */
    private fun comicByIDRequest(id: String): Request {
        val variables = Variables().set("comicId", id).build()
        val payload = Payload("comicById", variables, QUERY_COMIC_BY_ID)
        return POST(apiUrl, headers, payload.toRequestBody())
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

    /**
     * 檢查 API 是否達到上限
     * Check if the API has reached its limit.
     * TODO: how to throw an exception message to notify user in reading page?
     */
    private fun checkAPILimit(): Observable<Boolean> {
        val payload = Payload("reachedImageLimit", Variables.EMPTY, QUERY_API_LIMIT)
        val response = client.newCall(POST(apiUrl, headers, payload.toRequestBody()))
        val limit = response.asObservableSuccess().map { it.parseAs<Data<Boolean>>().data.result }
        return limit
    }

    // Popular Manga ===============================================================================

    override fun popularMangaRequest(page: Int): Request {
        val variables = Variables().set(
            "pagination",
            Pagination((page - 1) * PAGE_SIZE, "MONTH_VIEWS"),
        ).build()
        val payload = Payload("hotComics", variables, QUERY_HOT_COMICS)
        return POST(apiUrl, headers, payload.toRequestBody())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.parseAs<Data<List<Comic>>>()
        val comics = res.data.result
        return MangasPage(comics.map(Comic::toSManga), comics.size == PAGE_SIZE)
    }

    // Latest Updates ==============================================================================

    override fun latestUpdatesRequest(page: Int): Request {
        val variables = Variables().set(
            "pagination",
            Pagination((page - 1) * PAGE_SIZE, "DATE_UPDATED"),
        ).build()
        val payload = Payload("recentUpdate", variables, QUERY_RECENT_UPDATE)
        return POST(apiUrl, headers, payload.toRequestBody())
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search Manga ================================================================================

    override fun getFilterList() = buildFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val variables = Variables().set("keyword", query).build()
            val payload = Payload("searchComicAndAuthorQuery", variables, QUERY_SEARCH)
            return POST(apiUrl, headers, payload.toRequestBody())
        } else {
            val categories = filters[1] as CategoryFilter
            val variables = Variables().set(
                "pagination",
                Pagination(
                    (page - 1) * PAGE_SIZE,
                    filters[3].toString(),
                    status = filters[2].toString(),
                ),
            ).set("categoryId", categories.selected).build()
            val payload = Payload("comicByCategories", variables, QUERY_COMIC_BY_CATEGORY)
            return POST(apiUrl, headers, payload.toRequestBody())
        }
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
        var comics: List<Comic>
        try {
            val res = response.parseAs<Data<Result<List<Comic>>>>()
            comics = res.data.result.result
        } catch (e: Exception) {
            val res = response.parseAs<Data<List<Comic>>>()
            comics = res.data.result
        }
        return MangasPage(comics.map(Comic::toSManga), comics.size == PAGE_SIZE)
    }

    // Manga Details ===============================================================================

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga) = comicByIDRequest(manga.id)

    override fun mangaDetailsParse(response: Response): SManga {
        val res = response.parseAs<Data<Comic>>()
        return res.data.result.toSManga()
    }

    // Chapter List ================================================================================

    override fun chapterListRequest(manga: SManga): Request {
        val variables = Variables().set("comicId", manga.id).build()
        val payload = Payload("chapterByComicId", variables, QUERY_CHAPTER)
        return POST("$apiUrl#${manga.url}", headers, payload.toRequestBody())
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
        val comicUrl = response.request.url.fragment!!
        return items.map { it.toSChapter(comicUrl, DATE_FORMAT::tryParse) }
    }

    // Page List ===================================================================================

    override fun pageListRequest(chapter: SChapter): Request {
        val variables = Variables().set("chapterId", chapter.id).build()
        val payload = Payload("imagesByChapterId", variables, QUERY_PAGE_LIST)
        return POST("$apiUrl#${chapter.url}", headers, payload.toRequestBody())
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

    // Image =======================================================================================

    override fun imageRequest(page: Page): Request {
        return super.imageRequest(page).newBuilder()
            .addHeader(
                "accept",
                "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8'",
            )
            .addHeader("referer", page.url)
            .build()
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}

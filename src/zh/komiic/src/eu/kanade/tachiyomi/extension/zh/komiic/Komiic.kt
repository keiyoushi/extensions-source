package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Komiic : HttpSource() {
    // Override variables
    override var name = "Komiic"
    override val baseUrl = "https://komiic.com"
    override val lang = "zh"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Variables
    private val queryAPIUrl = "$baseUrl/api/query"
    private val json: Json by injectLazy()

    /**
     * 解析漫畫列表
     * Parse comic list
     */
    private inline fun <reified T : ComicListResult> parseComicList(response: Response): MangasPage {
        val res = response.parseAs<Data<T>>()
        val comics = res.data.comics

        val entries = comics.map { comic ->
            comic.toSManga()
        }

        val hasNextPage = comics.size == PAGE_SIZE
        return MangasPage(entries, hasNextPage)
    }

    // Hot Comic
    override fun popularMangaRequest(page: Int): Request {
        val payload = Payload(
            operationName = "hotComics",
            variables = HotComicsVariables(
                pagination = MangaListPagination(
                    PAGE_SIZE,
                    (page - 1) * PAGE_SIZE,
                    "MONTH_VIEWS",
                    "",
                    true,
                ),
            ),
            query = QUERY_HOT_COMICS,
        ).toJsonRequestBody()
        return POST(queryAPIUrl, headers, payload)
    }

    override fun popularMangaParse(response: Response) = parseComicList<HotComicsResponse>(response)

    // Recent update
    override fun latestUpdatesRequest(page: Int): Request {
        val payload = Payload(
            operationName = "recentUpdate",
            variables = RecentUpdateVariables(
                pagination = MangaListPagination(
                    PAGE_SIZE,
                    (page - 1) * PAGE_SIZE,
                    "DATE_UPDATED",
                    "",
                    true,
                ),
            ),
            query = QUERY_RECENT_UPDATE,
        ).toJsonRequestBody()
        return POST(queryAPIUrl, headers, payload)
    }

    override fun latestUpdatesParse(response: Response) = parseComicList<RecentUpdateResponse>(response)

    /**
     * 根據 ID 搜索漫畫
     * Search the comic based on the ID.
     */
    private fun comicByIDRequest(id: String): Request {
        val payload = Payload(
            operationName = "comicById",
            variables = ComicByIdVariables(id),
            query = QUERY_COMIC_BY_ID,
        ).toJsonRequestBody()
        return POST(queryAPIUrl, headers, payload)
    }

    /**
     * 根據 ID 解析搜索來的漫畫
     * Parse the comic based on the ID.
     */
    private fun parseComicByID(response: Response): MangasPage {
        val res = response.parseAs<Data<ComicByIDResponse>>()
        val entries = mutableListOf<SManga>()
        val comic = res.data.comic.toSManga()
        entries.add(comic)
        val hasNextPage = entries.size == PAGE_SIZE
        return MangasPage(entries, hasNextPage)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val payload = Payload(
            operationName = "searchComicAndAuthorQuery",
            variables = SearchVariables(query),
            query = QUERY_SEARCH,
        ).toJsonRequestBody()
        return POST(queryAPIUrl, headers, payload)
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val mangaId = query.substringAfter(PREFIX_ID_SEARCH)
            client.newCall(comicByIDRequest(mangaId))
                .asObservableSuccess()
                .map(::parseComicByID)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val res = response.parseAs<Data<SearchResponse>>()
        val comics = res.data.action.comics

        val entries = comics.map { comic ->
            comic.toSManga()
        }

        val hasNextPage = comics.size == PAGE_SIZE
        return MangasPage(entries, hasNextPage)
    }

    // Comic details
    override fun mangaDetailsRequest(manga: SManga) = comicByIDRequest(manga.url.substringAfterLast("/"))

    override fun mangaDetailsParse(response: Response): SManga {
        val res = response.parseAs<Data<ComicByIDResponse>>()
        val comic = res.data.comic.toSManga()
        return comic
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
        val payload = Payload(
            operationName = "chapterByComicId",
            variables = ChapterByComicIdVariables(manga.url.substringAfterLast("/")),
            query = QUERY_CHAPTER,
        ).toJsonRequestBody()

        return POST("$queryAPIUrl#${manga.url}", headers, payload)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val res = response.parseAs<Data<ChaptersResponse>>()
        val comics = res.data.chapters
        val comicUrl = response.request.url.fragment

        val tChapters = comics.filter { it.type == "chapter" }
        val tBooks = comics.filter { it.type == "book" }

        val entries = (tChapters + tBooks).map { chapter ->
            SChapter.create().apply {
                url = "$comicUrl/chapter/${chapter.id}/page/1"
                name = when (chapter.type) {
                    "chapter" -> "第 ${chapter.serial} 話"
                    "book" -> "第 ${chapter.serial} 卷"
                    else -> chapter.serial
                }
                date_upload = parseDate(chapter.dateCreated)
                chapter_number = chapter.serial.toFloatOrNull() ?: -1f
            }
        }.reversed()

        return entries
    }

    /**
     * 檢查 API 是否達到上限
     * Check if the API has reached its limit.
     *
     * (Idk how to throw an exception in reading page)
     */
    // private fun fetchAPILimit(): Boolean {
    //    val payload = Payload("getImageLimit", "", QUERY_API_LIMIT).toJsonRequestBody()
    //    val response = client.newCall(POST(queryAPIUrl, headers, payload)).execute()
    //    val limit = response.parseAs<APILimitData>().getImageLimit
    //    return limit.limit <= limit.usage
    // }

    // Page list
    override fun pageListRequest(chapter: SChapter): Request {
        val payload = Payload(
            operationName = "imagesByChapterId",
            variables = ImagesByChapterIdVariables(
                chapter.url.substringAfter("/chapter/").substringBefore("/page/"),
            ),
            query = QUERY_PAGE_LIST,
        ).toJsonRequestBody()

        return POST("$queryAPIUrl#${chapter.url}", headers, payload)
    }

    override fun pageListParse(response: Response): List<Page> {
        val res = response.parseAs<Data<ImagesResponse>>()
        val pages = res.data.images
        val chapterUrl = response.request.url.toString().split("#")[1]

        return pages.mapIndexed { index, image ->
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

    private inline fun <reified T> String.parseAs(): T =
        json.decodeFromString(this)

    private inline fun <reified T> Response.parseAs(): T =
        use { body.string() }.parseAs()

    private inline fun <reified T : Any> T.toJsonRequestBody(): RequestBody =
        json.encodeToString(this)
            .toRequestBody(JSON_MEDIA_TYPE)

    companion object {
        private const val PAGE_SIZE = 20
        const val PREFIX_ID_SEARCH = "id:"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

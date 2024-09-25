package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class Komiic : HttpSource() {
    /**
     * All generate by ChatGPT 4. But, it works...
     */
    override val name = "Komiic"
    override val baseUrl = "https://komiic.com"
    override val lang = "zh"
    override val supportsLatest = true

    private val queryAPI = "$baseUrl/api/query"

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().build()

    override fun headersBuilder() = super.headersBuilder()
        .add("content-type", "application/json")

    /**
     * 簡化數字顯示
     */
    private fun simplifyNumber(num: Int): String {
        return when {
            abs(num) < 1000 -> "$num"
            abs(num) < 10000 -> "${num / 1000}千"
            abs(num) < 100000000 -> "${num / 10000}萬"
            else -> "${num / 100000000}億"
        }
    }

    /**
     * 發送 Json Post 請求
     */
    private fun POSTJson(url: String, json: JsonObject): Request {
        return Request.Builder()
            .url(url)
            .headers(headers)
            .post(Json.encodeToString(JsonObject.serializer(), json).toRequestBody())
            .build()
    }

    /**
     * 解析作者
     */
    private fun parseAuthors(authors: JSONArray): String {
        return (0 until authors.length()).joinToString(", ") { authors.getJSONObject(it).getString("name") }
    }

    /**
     * 解析分類
     */
    private fun parseCategories(categories: JSONArray): String {
        return (0 until categories.length()).joinToString(", ") { categories.getJSONObject(it).getString("name") }
    }

    /**
     * 解析狀態
     */
    private fun parseStatus(status: String): Int {
        return when (status.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "END" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    /**
     * 解析漫畫列表
     */
    private fun parseMangaList(response: Response, operation: String): MangasPage {
        val mangas = mutableListOf<SManga>()
        val jsonData = response.body.string()
        val jsonObj = JSONObject(jsonData)
        val jsonArray = if (operation === "searchComicsAndAuthors") {
            jsonObj.getJSONObject("data").getJSONObject(operation).getJSONArray("comics")
        } else {
            jsonObj.getJSONObject("data").getJSONArray(operation)
        }

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val manga = SManga.create().apply {
                title = item.getString("title")
                thumbnail_url = item.getString("imageUrl")
                url = "/comic/${item.getString("id")}"
                author = parseAuthors(item.getJSONArray("authors"))
                genre = parseCategories(item.getJSONArray("categories"))
                status = parseStatus(item.getString("status"))
            }
            mangas.add(manga)
        }

        val hasNextPage = jsonArray.length() >= 20
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseMangaIDList(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()
        val jsonData = response.body.string()
        val jsonObj = JSONObject(jsonData)
        val jsonDict = jsonObj.getJSONObject("data").getJSONObject("comicById")

        val manga = SManga.create().apply {
            title = jsonDict.getString("title")
            thumbnail_url = jsonDict.getString("imageUrl")
            url = "/comic/${jsonDict.getString("id")}"
            author = parseAuthors(jsonDict.getJSONArray("authors"))
            genre = parseCategories(jsonDict.getJSONArray("categories"))
            status = parseStatus(jsonDict.getString("status"))
        }
        mangas.add(manga)

        val hasNextPage = jsonDict.length() >= 20
        return MangasPage(mangas, hasNextPage)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val data = buildJsonObject {
            put("operationName", "hotComics")
            putJsonObject("variables") {
                putJsonObject("pagination") { put("limit", 20); put("offset", (page - 1) * 20); put("orderBy", "VIEWS"); put("status", ""); put("asc", true) }
            }
            put("query", "query hotComics(\$pagination: Pagination!) {\n  hotComics(pagination: \$pagination) {\n    id\n    title\n    status\n    year\n    imageUrl\n    authors {\n      id\n      name\n      __typename\n    }\n    categories {\n      id\n      name\n      __typename\n    }\n    dateUpdated\n    monthViews\n    views\n    favoriteCount\n    lastBookUpdate\n    lastChapterUpdate\n    __typename\n  }\n}")
        }

        return client.newCall(POSTJson(queryAPI, data))
            .asObservableSuccess()
            .map { response ->
                parseMangaList(response, "hotComics")
            }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        val data = buildJsonObject {
            put("operationName", "recentUpdate")
            putJsonObject("variables") {
                putJsonObject("pagination") { put("limit", 20); put("offset", (page - 1) * 20); put("orderBy", "DATE_UPDATED"); put("status", ""); put("asc", true) }
            }
            put("query", "query recentUpdate(\$pagination: Pagination!) {\n  recentUpdate(pagination: \$pagination) {\n    id\n    title\n    status\n    year\n    imageUrl\n    authors {\n      id\n      name\n      __typename\n    }\n    categories {\n      id\n      name\n      __typename\n    }\n    dateUpdated\n    monthViews\n    views\n    favoriteCount\n    lastBookUpdate\n    lastChapterUpdate\n    __typename\n  }\n}")
        }

        return client.newCall(POSTJson(queryAPI, data))
            .asObservableSuccess()
            .map { response ->
                parseMangaList(response, "recentUpdate")
            }
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)

            val data = buildJsonObject {
                put("operationName", "comicById")
                putJsonObject("variables") {
                    put("comicId", id)
                }
                put("query", "query comicById(\$comicId: ID!) {\n  comicById(comicId: \$comicId) {\n    id\n    title\n    status\n    year\n    imageUrl\n    authors {\n      id\n      name\n      __typename\n    }\n    categories {\n      id\n      name\n      __typename\n    }\n    dateCreated\n    dateUpdated\n    views\n    favoriteCount\n    lastBookUpdate\n    lastChapterUpdate\n    __typename\n  }\n}")
            }

            return client.newCall(POSTJson(queryAPI, data))
                .asObservableSuccess()
                .map { response ->
                    parseMangaIDList(response)
                }
        } else {
            val data = buildJsonObject {
                put("operationName", "searchComicAndAuthorQuery")
                putJsonObject("variables") {
                    put("keyword", query)
                }
                put("query", "query searchComicAndAuthorQuery(\$keyword: String!) {\n  searchComicsAndAuthors(keyword: \$keyword) {\n    comics {\n      id\n      title\n      status\n      year\n      imageUrl\n      authors {\n        id\n        name\n        __typename\n      }\n      categories {\n        id\n        name\n        __typename\n      }\n      dateUpdated\n      monthViews\n      views\n      favoriteCount\n      lastBookUpdate\n      lastChapterUpdate\n      __typename\n    }\n    authors {\n      id\n      name\n      chName\n      enName\n      wikiLink\n      comicCount\n      views\n      __typename\n    }\n    __typename\n  }\n}")
            }

            return client.newCall(POSTJson(queryAPI, data))
                .asObservableSuccess()
                .map { response ->
                    parseMangaList(response, "searchComicsAndAuthors")
                }
        }
    }

    /**
     * 解析漫畫詳情
     */
    private fun parseMangaDetails(response: Response): SManga {
        val jsonData = response.body.string()
        val jsonObj = JSONObject(jsonData).getJSONObject("data").getJSONObject("comicById")

        return SManga.create().apply {
            url = "https://komiic.com/comic/${jsonObj.getString("id")}"
            title = jsonObj.getString("title")
            author = parseAuthors(jsonObj.getJSONArray("authors"))
            genre = parseCategories(jsonObj.getJSONArray("categories"))
            status = parseStatus(jsonObj.getString("status"))
            thumbnail_url = jsonObj.getString("imageUrl")
            description = buildString {
                append("年份: ${jsonObj.getString("year")} | ")
                append("點閱: ${simplifyNumber(jsonObj.getString("views").toInt())} | ")
                append("喜愛: ${simplifyNumber(jsonObj.getString("favoriteCount").toInt())}\n")
            }
        }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val data = buildJsonObject {
            put("operationName", "comicById")
            putJsonObject("variables") {
                put("comicId", manga.url.substringAfterLast("/"))
            }
            put("query", "query comicById(\$comicId: ID!) {\n  comicById(comicId: \$comicId) {\n    id\n    title\n    status\n    year\n    imageUrl\n    authors {\n      id\n      name\n      __typename\n    }\n    categories {\n      id\n      name\n      __typename\n    }\n    dateCreated\n    dateUpdated\n    views\n    favoriteCount\n    lastBookUpdate\n    lastChapterUpdate\n    __typename\n  }\n}")
        }

        return client.newCall(POSTJson(queryAPI, data))
            .asObservableSuccess()
            .map { response ->
                parseMangaDetails(response)
            }
    }

    /**
     * 解析日期
     */
    private fun parseDate(dateStr: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.parse(dateStr)?.time ?: 0L
    }

    /**
     * 解析章節列表
     */
    private fun parseChapterList(response: Response, mangaUrl: String): List<SChapter> {
        val jsonData = response.body.string()
        val jsonObj = JSONObject(jsonData).getJSONObject("data").getJSONArray("chaptersByComicId")
        val chapters = mutableListOf<SChapter>()
        val books = mutableListOf<SChapter>() // 用于存储所有的book类型章节

        // 循环遍历JSON数组
        for (i in jsonObj.length() - 1 downTo 0) {
            val chapterJson = jsonObj.getJSONObject(i)
            val chapter = SChapter.create().apply {
                url = "$mangaUrl/chapter/${chapterJson.getString("id")}/page/1"
                name = when (chapterJson.getString("type")) {
                    "chapter" -> "第 ${chapterJson.getString("serial")} 話"
                    "book" -> "第 ${chapterJson.getString("serial")} 卷"
                    else -> chapterJson.getString("serial")
                }
                date_upload = parseDate(chapterJson.getString("dateCreated"))
                chapter_number = chapterJson.getString("serial").toFloatOrNull() ?: -1f
            }

            // 根据类型添加到相应的列表
            if (chapterJson.getString("type") == "book") {
                books.add(chapter) // 先收集book类型章节
            } else {
                chapters.add(chapter) // 其他类型直接添加到列表末尾
            }
        }

        // 将books列表中的元素添加到chapters列表的最前面，保持原始顺序
        chapters.addAll(0, books)
        return chapters
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val data = buildJsonObject {
            put("operationName", "chapterByComicId")
            putJsonObject("variables") {
                put("comicId", manga.url.substringAfterLast("/"))
            }
            put("query", "query chapterByComicId(\$comicId: ID!) {\n  chaptersByComicId(comicId: \$comicId) {\n    id\n    serial\n    type\n    dateCreated\n    dateUpdated\n    size\n    __typename\n  }\n}")
        }

        return client.newCall(POSTJson(queryAPI, data))
            .asObservableSuccess()
            .map { response ->
                parseChapterList(response, manga.url)
            }
    }

    /**
     * 解析圖片列表
     */
    private fun parsePageList(response: Response, pageUrl: String): List<Page> {
        val pages = mutableListOf<Page>()
        val jsonData = response.body.string()
        val jsonImages = JSONObject(jsonData).getJSONObject("data").getJSONArray("imagesByChapterId")

        for (i in 0 until jsonImages.length()) {
            val imageJson = jsonImages.getJSONObject(i)
            val imageUrl = "https://komiic.com/api/image/${imageJson.getString("kid")}" // 使用详细的图片API获取图片URL
            pages.add(Page(i, pageUrl, imageUrl))
        }
        return pages.sortedBy { it.index } // 确保页面是排序的
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val data = buildJsonObject {
            put("operationName", "imagesByChapterId")
            putJsonObject("variables") {
                put("chapterId", chapter.url.substringAfter("/chapter/").substringBefore("/page/"))
            }
            put("query", "query imagesByChapterId(\$chapterId: ID!) {\n  imagesByChapterId(chapterId: \$chapterId) {\n    id\n    kid\n    height\n    width\n    __typename\n  }\n}")
        }

        return client.newCall(POSTJson(queryAPI, data))
            .asObservableSuccess()
            .map { response ->
                parsePageList(
                    response,
                    chapter.url,
                )
            }
    }

    override fun imageRequest(page: Page): Request {
        return super.imageRequest(page).newBuilder()
            .addHeader("accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8'")
            .addHeader("referer", page.url)
            .build()
    }

    override fun imageUrlParse(response: Response): String {
        TODO("Not yet implemented")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        TODO("Not yet implemented")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        TODO("Not yet implemented")
    }

    override fun pageListParse(response: Response): List<Page> {
        TODO("Not yet implemented")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        TODO("Not yet implemented")
    }

    override fun popularMangaRequest(page: Int): Request {
        TODO("Not yet implemented")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        TODO("Not yet implemented")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("Not yet implemented")
    }
}

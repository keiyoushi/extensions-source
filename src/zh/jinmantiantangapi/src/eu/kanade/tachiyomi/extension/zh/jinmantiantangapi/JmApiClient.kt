package eu.kanade.tachiyomi.extension.zh.jinmantiantangapi
import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
/**
 * 禁漫天堂 API 客户端
 *
 * 封装所有 API 请求，提供类型安全的接口
 */
class JmApiClient(
    private val client: OkHttpClient,
    private val preferences: SharedPreferences,
) {
    private fun cleanString(value: String?): String = value
        ?.trim()
        ?.takeUnless { it.equals("null", ignoreCase = true) }
        .orEmpty()

    private fun JSONArray.joinSafeStrings(): String = (0 until length())
        .mapNotNull { index ->
            val value = opt(index)
            val text = when (value) {
                null, JSONObject.NULL -> null
                is String -> cleanString(value)
                is JSONObject -> cleanString(value.optString("name", value.optString("title", value.toString())))
                else -> cleanString(value.toString())
            }
            text?.takeIf { it.isNotBlank() }
        }
        .joinToString(", ")

    // 初始化标志
    @Volatile
    private var initialized = false

    @Volatile
    private var cachedImageHost: String = ""
    private val initLock = Any()

    /**
     * 确保API已初始化
     * 禁漫天堂API需要先调用/setting端点建立会话
     */
    private fun ensureInitialized() {
        if (initialized) return
        synchronized(initLock) {
            if (initialized) return
            try {
                // 调用/setting端点初始化会话
                val json = executeGet(JmConstants.ENDPOINT_SETTING)
                // 检查初始化是否成功
                checkResponse(json)
                val settingData = json.optJSONObject("data")
                cachedImageHost = cleanString(settingData?.optString("img_host", ""))
                initialized = true
            } catch (e: Exception) {
                // 初始化失败不阻塞，让后续请求自己处理错误
                initialized = false
            }
        }
    }

    /**
     * 获取当前 API 域名
     */
    private fun getApiDomain(): String {
        val domainList = preferences.getString(
            JmConstants.PREF_API_DOMAIN_LIST,
            JmConstants.API_DOMAIN_LIST.joinToString(","),
        )?.split(",") ?: JmConstants.API_DOMAIN_LIST.toList()

        val index = preferences.getApiDomainIndex()
        return domainList.getOrNull(index) ?: domainList.first()
    }

    /**
     * 获取 API 基础 URL
     */
    private fun getBaseUrl(): String = "https://${getApiDomain()}"

    /**
     * 获取图片主机（来自 /setting）
     */
    private fun getImageHost(): String {
        val host = cachedImageHost.trim().trimEnd('/')
        if (host.isEmpty()) return ""
        return if (host.startsWith("http")) host else "https://$host"
    }

    /**
     * 执行 GET 请求并返回 JSON 响应
     */
    private fun executeGet(endpoint: String, params: Map<String, String> = emptyMap()): JSONObject {
        val url = buildUrl(endpoint, params)
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("API 请求失败: HTTP ${response.code}")
            }

            val body = response.body?.string() ?: throw Exception("响应体为空")
            JSONObject(body)
        }
    }

    /**
     * 构建完整 URL
     */
    private fun buildUrl(endpoint: String, params: Map<String, String>): String {
        if (params.isEmpty()) return "${getBaseUrl()}$endpoint"

        val url = StringBuilder("${getBaseUrl()}$endpoint?")
        params.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) url.append('&')
            url.append(key)
            url.append('=')
            url.append(if (key == "search_query") encodeSearchQuery(value) else value.toHttpUrlQuery())
        }
        return url.toString()
    }

    /**
     * 禁漫搜索语法依赖特殊编码：
     * - 空格 -> + (OR)
     * - +号 -> %2B (AND 运算符字面量)
     */
    private fun encodeSearchQuery(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun String.toHttpUrlQuery(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    /**
     * 检查响应状态
     */
    private fun checkResponse(json: JSONObject) {
        val code = json.optInt("code", -1)
        if (code != 200) {
            // API 返回的错误字段是 errorMsg，不是 message
            val message = json.optString("errorMsg", json.optString("message", "未知错误"))
            throw Exception("API 错误: $message (code: $code)")
        }
    }

    /**
     * 获取响应中的 data 字段
     */
    private fun getData(json: JSONObject): JSONObject {
        checkResponse(json)
        return json.optJSONObject("data") ?: throw Exception("响应缺少 data 字段")
    }

    /**
     * 搜索漫画
     *
     * @param query 搜索关键词
     * @param page 页码（从 1 开始）
     * @return 漫画列表页
     */
    fun search(
        query: String,
        page: Int,
        mainTag: String = "0",
        sortBy: String = "mr",
        time: String = "",
    ): MangasPage {
        ensureInitialized() // 确保会话已初始化
        val params = mutableMapOf(
            "search_query" to query,
            "page" to page.toString(),
            "main_tag" to mainTag,
            "o" to sortBy,
        )
        if (time.isNotEmpty()) params["t"] = time
        val json = executeGet(JmConstants.ENDPOINT_SEARCH, params)
        val data = getData(json)
        return parseMangaList(data)
    }

    /**
     * 获取分类筛选列表
     *
     * @param categoryId 分类 ID
     * @param page 页码
     * @param sortBy 排序方式（mr=最新, mv=最多浏览, tf=最多爱心）
     * @return 漫画列表页
     */
    fun getCategoryFilter(
        categoryId: String = "",
        page: Int = 1,
        sortBy: String = "mr",
        time: String = "",
    ): MangasPage {
        ensureInitialized() // 确保会话已初始化
        val params = mutableMapOf(
            "page" to page.toString(),
            "o" to sortBy,
        )
        if (categoryId.isNotEmpty()) {
            params["c"] = categoryId
        }
        if (time.isNotEmpty()) {
            params["t"] = time
        }
        val json = executeGet(JmConstants.ENDPOINT_CATEGORIES_FILTER, params)
        val data = getData(json)
        return parseMangaList(data)
    }

    /**
     * 获取收藏列表
     *
     * @param page 页码
     * @return 漫画列表页
     */
    fun getFavorites(page: Int): MangasPage {
        ensureInitialized() // 确保会话已初始化
        val params = mapOf("page" to page.toString())
        val json = executeGet(JmConstants.ENDPOINT_FAVORITE, params)
        val data = getData(json)
        // 收藏列表使用 list 字段而不是 content
        val list = data.optJSONArray("list") ?: JSONArray()
        val mangas = mutableListOf<SManga>()
        for (i in 0 until list.length()) {
            try {
                val item = list.getJSONObject(i)
                mangas.add(parseManga(item))
            } catch (_: Exception) {
                continue
            }
        }
        val hasNextPage = data.optInt("total", 0) > page * 20
        return MangasPage(mangas, hasNextPage)
    }

    /**
     * 获取漫画详情
     *
     * @param albumId 漫画 ID
     * @return 漫画详情
     */
    fun getAlbumDetail(albumId: String): SManga {
        ensureInitialized() // 确保会话已初始化
        val json = executeGet(JmConstants.ENDPOINT_ALBUM, mapOf("id" to albumId))
        val data = getData(json)
        return parseMangaDetail(data)
    }

    /**
     * 获取章节列表
     *
     * @param albumId 漫画 ID
     * @return 章节列表
     */
    fun getChapterList(albumId: String): List<SChapter> {
        ensureInitialized() // 确保会话已初始化
        val json = executeGet(JmConstants.ENDPOINT_ALBUM, mapOf("id" to albumId))
        val data = getData(json)
        return parseChapterList(data, albumId)
    }

    /**
     * 获取章节图片列表
     *
     * @param chapterId 章节 ID
     * @return 图片页列表
     */
    fun getChapterPages(chapterId: String): List<Page> {
        ensureInitialized() // 确保会话已初始化
        val json = executeGet(JmConstants.ENDPOINT_CHAPTER, mapOf("id" to chapterId, "mode" to "vertical"))
        val data = getData(json)
        return parsePageList(data)
    }

    /**
     * 解析漫画列表
     */
    private fun parseMangaList(data: JSONObject): MangasPage {
        val content = data.optJSONArray("content") ?: JSONArray()
        val mangas = mutableListOf<SManga>()

        for (i in 0 until content.length()) {
            try {
                val item = content.getJSONObject(i)
                mangas.add(parseManga(item))
            } catch (e: Exception) {
                // 跳过无效条目
                continue
            }
        }

        val hasNextPage = data.optInt("total", 0) > data.optInt("page", 1) * 20
        return MangasPage(mangas, hasNextPage)
    }

    /**
     * 解析单个漫画信息（列表项）
     */
    private fun parseManga(json: JSONObject): SManga = SManga.create().apply {
        val id = cleanString(json.optString("id", json.optString("aid", "")))
        url = "/album/$id"
        title = cleanString(json.optString("name", json.optString("title", "")))

        // 缩略图
        val imageUrl = cleanString(json.optString("image", ""))
        thumbnail_url = if (imageUrl.isNotEmpty()) {
            imageUrl.substringBeforeLast('.') + "_3x4.jpg"
        } else if (id.isNotEmpty() && getImageHost().isNotEmpty()) {
            "${getImageHost()}/media/albums/${id}_3x4.jpg"
        } else {
            ""
        }

        // 作者
        val authorArray = json.optJSONArray("author")
        author = if (authorArray != null && authorArray.length() > 0) {
            authorArray.joinSafeStrings()
        } else if (cleanString(json.optString("author", "")).isNotEmpty()) {
            cleanString(json.optString("author", ""))
        } else {
            ""
        }

        // 标签
        val tagsArray = json.optJSONArray("tags")
        genre = if (tagsArray != null && tagsArray.length() > 0) {
            tagsArray.joinSafeStrings()
        } else {
            buildList {
                cleanString(json.optJSONObject("category")?.optString("title")).takeIf { it.isNotBlank() }?.let(::add)
                cleanString(json.optJSONObject("category_sub")?.optString("title")).takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(", ")
        }
    }

    /**
     * 解析漫画详情
     */
    private fun parseMangaDetail(data: JSONObject): SManga = SManga.create().apply {
        val id = cleanString(data.optString("id", ""))
        url = "/album/$id"
        title = cleanString(data.optString("name", data.optString("title", "")))

        // 缩略图
        val imageUrl = cleanString(data.optString("image", ""))
        thumbnail_url = if (imageUrl.isNotEmpty()) {
            imageUrl.substringBeforeLast('.') + "_3x4.jpg"
        } else if (id.isNotEmpty() && getImageHost().isNotEmpty()) {
            "${getImageHost()}/media/albums/${id}_3x4.jpg"
        } else {
            ""
        }

        // 作者
        val authorArray = data.optJSONArray("author")
        author = if (authorArray != null && authorArray.length() > 0) {
            authorArray.joinSafeStrings()
        } else {
            cleanString(data.optString("author", ""))
        }

        // 标签
        val tagsArray = data.optJSONArray("tags")
        genre = if (tagsArray != null && tagsArray.length() > 0) {
            tagsArray.joinSafeStrings()
        } else {
            buildList {
                cleanString(data.optJSONObject("category")?.optString("title")).takeIf { it.isNotBlank() }?.let(::add)
                cleanString(data.optJSONObject("category_sub")?.optString("title")).takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(", ")
        }

        // 状态
        status = when (data.optString("status", "")) {
            "連載中" -> SManga.ONGOING
            "完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        // 描述
        description = data.optString("description", "")
    }

    /**
     * 解析章节列表
     */
    private fun parseChapterList(data: JSONObject, albumId: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        // 检查是否有章节列表
        val episodeArray = data.optJSONArray("episode") ?: data.optJSONArray("series")

        if (episodeArray == null || episodeArray.length() == 0) {
            // 单章节漫画
            val chapter = SChapter.create().apply {
                val fallbackChapterId = cleanString(data.optString("id", ""))
                    .takeIf { it.isNotBlank() && it != "0" }
                    ?: cleanString(data.optString("series_id", "")).takeIf { it.isNotBlank() && it != "0" }
                    ?: albumId
                url = "/chapter/$fallbackChapterId"
                name = "单章节"
                chapter_number = 1f
                date_upload = parseDate(data.optString("created_at", data.optString("addtime", "")))
            }
            chapters.add(chapter)
        } else {
            // 多章节漫画
            for (i in 0 until episodeArray.length()) {
                try {
                    val episode = episodeArray.getJSONObject(i)
                    val chapter = SChapter.create().apply {
                        val chapterId = cleanString(episode.optString("id", ""))
                        url = "/chapter/$chapterId"
                        name = cleanString(episode.optString("name", episode.optString("title", "第${i + 1}话")))
                            .ifEmpty { "第${i + 1}话" }
                        chapter_number = (i + 1).toFloat()
                        date_upload = parseDate(episode.optString("created_at", episode.optString("addtime", "")))
                    }
                    chapters.add(chapter)
                } catch (e: Exception) {
                    // 跳过无效章节
                    continue
                }
            }
        }

        return chapters.reversed() // 最新章节在前
    }

    /**
     * 解析图片页列表
     */
    private fun parsePageList(data: JSONObject): List<Page> {
        val pages = mutableListOf<Page>()

        // 获取图片数组
        val imagesArray = data.optJSONArray("images")
            ?: throw Exception("章节数据缺少 images 字段")

        val chapterId = cleanString(data.optString("id", ""))
            .takeIf { it.isNotBlank() && it != "0" }
            ?: cleanString(data.optString("series_id", "")).takeIf { it.isNotBlank() && it != "0" }
            ?: ""
        if (chapterId.isEmpty()) {
            throw Exception("章节数据缺少 id 字段")
        }

        // 图片域名优先使用章节字段，缺失时回退到 /setting 的 img_host
        val imageHost = cleanString(data.optString("image_domain", data.optString("domain", "")))
            .ifEmpty { getImageHost() }
            .trimEnd('/')
        if (imageHost.isEmpty()) {
            throw Exception("章节数据缺少 image_domain 字段")
        }

        // 构建图片 URL
        for (i in 0 until imagesArray.length()) {
            try {
                val imagePath = cleanString(imagesArray.getString(i))
                if (imagePath.isEmpty()) continue
                val normalizedPath = if (imagePath.startsWith("/")) imagePath else "/media/photos/$chapterId/$imagePath"
                val imageUrl = if (normalizedPath.startsWith("http")) normalizedPath else "$imageHost$normalizedPath"
                pages.add(Page(i, "", imageUrl))
            } catch (e: Exception) {
                // 跳过无效图片
                continue
            }
        }

        return pages
    }

    /**
     * 解析日期字符串
     *
     * @param dateString 日期字符串（格式：yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）
     * @return 时间戳（毫秒）
     */
    private fun parseDate(dateString: String): Long {
        if (dateString.isEmpty()) return 0L

        dateString.toLongOrNull()?.let { value ->
            return if (value > 1_000_000_000_000L) value else value * 1000L
        }

        return try {
            val date = dateString.substringBefore(" ") // 只取日期部分
            val parts = date.split("-")
            if (parts.size != 3) return 0L

            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = parts[2].toInt()

            // 简单的日期转时间戳（不考虑时区）
            val calendar = java.util.Calendar.getInstance()
            calendar.set(year, month - 1, day, 0, 0, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }
}

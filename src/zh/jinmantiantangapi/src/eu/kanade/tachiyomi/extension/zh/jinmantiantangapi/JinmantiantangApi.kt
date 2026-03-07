package eu.kanade.tachiyomi.extension.zh.jinmantiantangapi

import android.app.Application
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * 禁漫天堂 API 版插件
 *
 * 基于移动端 API 实现
 * 与网页版插件并存，专注于 API 版浏览体验
 */
class JinmantiantangApi :
    HttpSource(),
    ConfigurableSource {

    companion object {
        // 登录功能已移除，保留旧键名用于清理历史明文凭证
        private const val LEGACY_PREF_USERNAME = "username"
        private const val LEGACY_PREF_PASSWORD = "password"
    }

    override val name = "禁漫天堂(API)"
    override val lang = "zh"
    override val supportsLatest = true

    // SharedPreferences
    private val preferences = Injekt.get<Application>()
        .getSharedPreferences("source_$id", 0x0000)

    // Cookie 管理器
    private val cookieJar = JmCookieJar(preferences)

    // API 签名拦截器
    private val signatureInterceptor = ApiSignatureInterceptor()

    // 响应解密拦截器
    private val responseInterceptor = ApiResponseInterceptor()

    // 自定义 OkHttpClient
    override val client: OkHttpClient = network.cloudflareClient
        .newBuilder()
        .cookieJar(cookieJar)
        .addInterceptor(signatureInterceptor)
        .addInterceptor(responseInterceptor)
        .addInterceptor(ScrambledImageInterceptor)
        .build()

    // 域名管理器
    private val domainManager = DomainManager(client, preferences)

    // API 客户端
    private val apiClient = JmApiClient(client, preferences)

    init {
        // 尝试更新域名列表（后台执行，不阻塞初始化）
        try {
            domainManager.tryUpdateDomains()
        } catch (e: Exception) {
            // 更新失败不影响扩展使用
        }

        // 移除已废弃的登录功能残留凭证（明文）
        preferences.edit()
            .remove(LEGACY_PREF_USERNAME)
            .remove(LEGACY_PREF_PASSWORD)
            .apply()
    }

    // 基础 URL（动态获取）
    override val baseUrl: String
        get() {
            val domainList = preferences.getString(
                JmConstants.PREF_API_DOMAIN_LIST,
                JmConstants.API_DOMAIN_LIST.joinToString(","),
            )?.split(",") ?: JmConstants.API_DOMAIN_LIST.toList()

            val index = preferences.getApiDomainIndex()
            val domain = domainList.getOrNull(index) ?: domainList.first()
            return "https://$domain"
        }

    // ==================== 热门漫画 ====================

    override fun popularMangaRequest(page: Int): Request {
        // 使用 API 的分类筛选接口，按浏览量排序
        return GET("$baseUrl${JmConstants.ENDPOINT_CATEGORIES_FILTER}?page=$page&o=mv", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        // 从 URL 中提取页码
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        return apiClient.getCategoryFilter(page = page, sortBy = "mv")
    }

    // ==================== 最新更新 ====================

    override fun latestUpdatesRequest(page: Int): Request {
        // 使用 API 的分类筛选接口，按最新排序
        return GET("$baseUrl${JmConstants.ENDPOINT_CATEGORIES_FILTER}?page=$page&o=mr", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        // 从 URL 中提取页码
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        return apiClient.getCategoryFilter(page = page, sortBy = "mr")
    }

    // ==================== 搜索 ====================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val parsedFilters = filters.toApiSearchFilters()
        val textQuery = query
        val filterKeyword = parsedFilters.categoryKeyword.trim()
        val mergedQuery = when {
            textQuery.isNotBlank() && filterKeyword.isNotBlank() -> "$textQuery +$filterKeyword"
            textQuery.isNotBlank() -> textQuery
            filterKeyword.isNotBlank() -> filterKeyword
            else -> ""
        }

        val isCategoryListingMode = mergedQuery.isBlank()

        val url = if (isCategoryListingMode) {
            "$baseUrl${JmConstants.ENDPOINT_CATEGORIES_FILTER}".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("o", parsedFilters.sortBy)
                .apply {
                    addQueryParameter("c", parsedFilters.categoryId)
                    parsedFilters.time.takeIf { it.isNotBlank() }?.let { addQueryParameter("t", it) }
                }
                .build()
                .toString()
        } else {
            "$baseUrl${JmConstants.ENDPOINT_SEARCH}".toHttpUrl().newBuilder()
                .addQueryParameter("search_query", mergedQuery)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("main_tag", parsedFilters.mainTag)
                .addQueryParameter("o", parsedFilters.sortBy)
                .apply {
                    parsedFilters.time.takeIf { it.isNotBlank() }?.let { addQueryParameter("t", it) }
                }
                .build()
                .toString()
        }

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        // 从 URL 中提取搜索参数
        val url = response.request.url
        val query = url.queryParameter("search_query") ?: ""
        val page = url.queryParameter("page")?.toIntOrNull() ?: 1
        val sortBy = url.queryParameter("o") ?: "mr"
        val time = url.queryParameter("t") ?: ""

        return if (url.encodedPath.endsWith(JmConstants.ENDPOINT_CATEGORIES_FILTER)) {
            apiClient.getCategoryFilter(
                categoryId = url.queryParameter("c") ?: "",
                page = page,
                sortBy = sortBy,
                time = time,
            )
        } else {
            apiClient.search(
                query = query,
                page = page,
                mainTag = url.queryParameter("main_tag") ?: "0",
                sortBy = sortBy,
                time = time,
            )
        }
    }

    // ==================== 漫画详情 ====================

    override fun mangaDetailsRequest(manga: SManga): Request {
        // 从 URL 中提取漫画 ID
        val albumId = manga.url.substringAfter("/album/").substringBefore("/")
        return GET("$baseUrl${JmConstants.ENDPOINT_ALBUM}?id=$albumId", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        // 从 URL 中提取漫画 ID
        val albumId = response.request.url.queryParameter("id") ?: response.request.url.pathSegments.last()
        return apiClient.getAlbumDetail(albumId)
    }

    // ==================== 章节列表 ====================

    override fun chapterListRequest(manga: SManga): Request {
        // 从 URL 中提取漫画 ID
        val albumId = manga.url.substringAfter("/album/").substringBefore("/")
        return GET("$baseUrl${JmConstants.ENDPOINT_ALBUM}?id=$albumId", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        // 从 URL 中提取漫画 ID
        val albumId = response.request.url.queryParameter("id") ?: response.request.url.pathSegments.last()
        return apiClient.getChapterList(albumId)
    }

    // ==================== 图片列表 ====================

    override fun pageListRequest(chapter: SChapter): Request {
        // 从 URL 中提取章节 ID
        val chapterId = chapter.url.substringAfter("/chapter/").substringBefore("/")
        return GET("$baseUrl${JmConstants.ENDPOINT_CHAPTER}?id=$chapterId&mode=vertical", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        // 从 URL 中提取章节 ID
        val chapterId = response.request.url.queryParameter("id") ?: response.request.url.pathSegments.last()
        return apiClient.getChapterPages(chapterId)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // ==================== 设置界面 ====================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferenceList(screen.context, preferences).forEach(screen::addPreference)
    }

    // ==================== 过滤器 ====================

    override fun getFilterList() = FilterList(
        ApiCategoryFilter(),
        ApiSortFilter(),
        ApiTimeFilter(),
        ApiTypeFilter(),
    )

    private data class ApiSearchFilters(
        val categoryId: String = "",
        val categoryKeyword: String = "",
        val sortBy: String = "mr",
        val time: String = "",
        val mainTag: String = "0",
    )

    private fun FilterList.toApiSearchFilters(): ApiSearchFilters {
        val category = filterOrNull<ApiCategoryFilter>()?.selected ?: CategoryOption.ALL
        val sort = filterOrNull<ApiSortFilter>()?.selectedValue ?: "mr"
        val time = filterOrNull<ApiTimeFilter>()?.selectedValue ?: ""
        val mainTag = filterOrNull<ApiTypeFilter>()?.selectedValue ?: "0"
        return ApiSearchFilters(
            categoryId = category.categoryId,
            categoryKeyword = category.keyword,
            sortBy = sort,
            time = time,
            mainTag = mainTag,
        )
    }

    private inline fun <reified T> FilterList.filterOrNull(): T? = firstOrNull { it is T } as? T

    private data class CategoryOption(
        val label: String,
        val categoryId: String = "",
        val keyword: String = "",
    ) {
        companion object {
            val ALL = CategoryOption("全部")
        }
    }

    private class ApiCategoryFilter :
        Filter.Select<String>(
            "按类型",
            OPTIONS.map { it.label }.toTypedArray(),
        ) {
        val selected: CategoryOption get() = OPTIONS.getOrElse(state) { CategoryOption.ALL }

        companion object {
            val OPTIONS = listOf(
                CategoryOption.ALL,
                CategoryOption("其他", categoryId = "another"),
                CategoryOption("同人", categoryId = "doujin"),
                CategoryOption("韩漫", categoryId = "hanman"),
                CategoryOption("美漫", categoryId = "meiman"),
                CategoryOption("短篇", categoryId = "short"),
                CategoryOption("单本", categoryId = "single"),
                // 移动端 /categories/filter 对 cosplay / CG 的 c 参数结果不稳定，改走关键词搜索更接近原版体验
                CategoryOption("Cosplay", keyword = "Cosplay"),
                CategoryOption("CG", keyword = "CG"),
                CategoryOption("P站", keyword = "PIXIV"),
                CategoryOption("3D", keyword = "3D"),
                CategoryOption("剧情", keyword = "劇情"),
                CategoryOption("校园", keyword = "校園"),
                CategoryOption("纯爱", keyword = "純愛"),
                CategoryOption("人妻", keyword = "人妻"),
                CategoryOption("师生", keyword = "師生"),
                CategoryOption("乱伦", keyword = "亂倫"),
                CategoryOption("近亲", keyword = "近親"),
                CategoryOption("百合", keyword = "百合"),
                CategoryOption("男同", keyword = "YAOI"),
                CategoryOption("性转", keyword = "性轉"),
                CategoryOption("NTR", keyword = "NTR"),
                CategoryOption("伪娘", keyword = "偽娘"),
                CategoryOption("痴女", keyword = "癡女"),
                CategoryOption("全彩", keyword = "全彩"),
                CategoryOption("女性向", keyword = "女性向"),
                CategoryOption("萝莉", keyword = "蘿莉"),
                CategoryOption("御姐", keyword = "御姐"),
                CategoryOption("熟女", keyword = "熟女"),
                CategoryOption("正太", keyword = "正太"),
                CategoryOption("巨乳", keyword = "巨乳"),
                CategoryOption("贫乳", keyword = "貧乳"),
                CategoryOption("女王", keyword = "女王"),
                CategoryOption("教师", keyword = "教師"),
                CategoryOption("女仆", keyword = "女僕"),
                CategoryOption("护士", keyword = "護士"),
                CategoryOption("泳裝", keyword = "泳裝"),
                CategoryOption("眼镜", keyword = "眼鏡"),
                CategoryOption("丝袜", keyword = "絲襪"),
                CategoryOption("连裤袜", keyword = "連褲襪"),
                CategoryOption("制服", keyword = "制服"),
                CategoryOption("兔女郎", keyword = "兔女郎"),
                CategoryOption("群交", keyword = "群交"),
                CategoryOption("足交", keyword = "足交"),
                CategoryOption("SM", keyword = "SM"),
                CategoryOption("肛交", keyword = "肛交"),
                CategoryOption("阿黑颜", keyword = "阿黑顏"),
                CategoryOption("药物", keyword = "藥物"),
                CategoryOption("扶他", keyword = "扶他"),
                CategoryOption("调教", keyword = "調教"),
                CategoryOption("野外", keyword = "野外"),
                CategoryOption("露出", keyword = "露出"),
                CategoryOption("催眠", keyword = "催眠"),
                CategoryOption("自慰", keyword = "自慰"),
                CategoryOption("触手", keyword = "觸手"),
                CategoryOption("兽交", keyword = "獸交"),
                CategoryOption("亚人", keyword = "亞人"),
                CategoryOption("魔物", keyword = "魔物"),
                CategoryOption("CG 全彩", keyword = "CG 全彩"),
                CategoryOption("重口", keyword = "重口"),
                CategoryOption("猎奇", keyword = "獵奇"),
                CategoryOption("非H", keyword = "非H"),
                CategoryOption("血腥", keyword = "血腥"),
                CategoryOption("暴力", keyword = "暴力"),
                CategoryOption("血腥暴力", keyword = "血腥暴力"),
            )
        }
    }

    private abstract class ApiValueFilter(
        name: String,
        private val items: Array<Pair<String, String>>,
    ) : Filter.Select<String>(name, items.map { it.first }.toTypedArray()) {
        val selectedValue: String get() = items.getOrElse(state) { items.first() }.second
    }

    private class ApiSortFilter :
        ApiValueFilter(
            "排序",
            arrayOf(
                "最新" to "mr",
                "最多浏览" to "mv",
                "最多爱心" to "tf",
                "最多图片" to "mp",
            ),
        )

    private class ApiTimeFilter :
        ApiValueFilter(
            "时间",
            arrayOf(
                "全部" to "",
                "今天" to "t",
                "这周" to "w",
                "本月" to "m",
            ),
        )

    private class ApiTypeFilter :
        ApiValueFilter(
            "搜索范围",
            arrayOf(
                "站内搜索" to "0",
                "作品" to "1",
                "作者" to "2",
                "标签" to "3",
                "登场人物" to "4",
            ),
        )
}
